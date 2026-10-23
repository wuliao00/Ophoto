package com.ophoto.core.security

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

enum class SandboxViolation {
    FILE_TOO_LARGE,
    PATH_TRAVERSAL,
    FORBIDDEN_EXECUTABLE_OR_SCRIPT,
    TOO_MANY_ARCHIVE_ENTRIES,
    ARCHIVE_BOMB_DETECTED,
    IO_FAILURE,
}

sealed class SandboxResult<out T> {
    data class Valid<T>(val value: T) : SandboxResult<T>()
    data class Rejected(val reason: SandboxViolation, val detail: String) : SandboxResult<Nothing>()
}

object FileSandboxValidator {

    const val MAX_SINGLE_FILE_BYTES: Long = 10L * 1024L * 1024L // 10 MB limit per Doc §4
    const val MAX_ARCHIVE_TOTAL_BYTES: Long = 28L * 1024L * 1024L
    const val MAX_ARCHIVE_ENTRIES: Int = 32

    private val FORBIDDEN_EXTENSIONS = setOf(
        "apk", "dex", "so", "sh", "bash", "bat", "cmd", "ps1", "exe",
        "dll", "js", "mjs", "py", "rb", "php", "jar", "class", "html", "htm", "svg",
    )

    fun validateEntryPath(rawEntryName: String, targetRootDir: File): SandboxResult<File> {
        val trimmed = rawEntryName.trim()
        if (trimmed.isEmpty()) {
            return SandboxResult.Rejected(SandboxViolation.PATH_TRAVERSAL, "Empty archive path")
        }
        if (trimmed.startsWith("/") || trimmed.startsWith("\\") || trimmed.contains("\\") || trimmed.contains("..")) {
            return SandboxResult.Rejected(
                SandboxViolation.PATH_TRAVERSAL,
                "Path traversal detected: $trimmed",
            )
        }
        val lowerExt = trimmed.substringAfterLast('.', "").lowercase()
        if (lowerExt in FORBIDDEN_EXTENSIONS) {
            return SandboxResult.Rejected(
                SandboxViolation.FORBIDDEN_EXECUTABLE_OR_SCRIPT,
                "Executable or script file prohibited: .$lowerExt",
            )
        }
        val rootCanonical = targetRootDir.canonicalFile
        val destCanonical = File(rootCanonical, trimmed).canonicalFile
        val rootPrefix = rootCanonical.path + File.separator
        if (destCanonical != rootCanonical && !destCanonical.path.startsWith(rootPrefix)) {
            return SandboxResult.Rejected(
                SandboxViolation.PATH_TRAVERSAL,
                "Canonical path escaped sandbox: $trimmed",
            )
        }
        return SandboxResult.Valid(destCanonical)
    }

    fun copyLimitedStream(
        input: InputStream,
        destination: File,
        maxBytes: Long = MAX_SINGLE_FILE_BYTES,
    ): SandboxResult<String> {
        return try {
            destination.parentFile?.mkdirs()
            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            val buffer = ByteArray(8192)
            destination.outputStream().use { out ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    totalBytes += read
                    if (totalBytes > maxBytes) {
                        destination.delete()
                        return SandboxResult.Rejected(
                            SandboxViolation.FILE_TOO_LARGE,
                            "File exceeds ${maxBytes / (1024 * 1024)}MB safety threshold",
                        )
                    }
                    digest.update(buffer, 0, read)
                    out.write(buffer, 0, read)
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            SandboxResult.Valid(hex)
        } catch (e: Exception) {
            destination.delete()
            SandboxResult.Rejected(SandboxViolation.IO_FAILURE, e.message ?: "Stream read failure")
        }
    }

    fun extractArchiveSafely(
        archiveStream: InputStream,
        destinationDir: File,
    ): SandboxResult<List<File>> {
        destinationDir.mkdirs()
        val extractedFiles = mutableListOf<File>()
        var entryCount = 0
        var totalUncompressed = 0L
        val buffer = ByteArray(8192)

        return try {
            ZipInputStream(archiveStream.buffered()).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_ARCHIVE_ENTRIES) {
                        destinationDir.deleteRecursively()
                        return SandboxResult.Rejected(
                            SandboxViolation.TOO_MANY_ARCHIVE_ENTRIES,
                            "Archive contains more than $MAX_ARCHIVE_ENTRIES entries",
                        )
                    }

                    val check = validateEntryPath(entry.name, destinationDir)
                    val safeDest = when (check) {
                        is SandboxResult.Valid -> check.value
                        is SandboxResult.Rejected -> {
                            destinationDir.deleteRecursively()
                            return check
                        }
                    }

                    if (entry.isDirectory) {
                        safeDest.mkdirs()
                    } else {
                        safeDest.parentFile?.mkdirs()
                        var entryBytes = 0L
                        safeDest.outputStream().use { out ->
                            while (true) {
                                val read = zis.read(buffer)
                                if (read <= 0) break
                                entryBytes += read
                                totalUncompressed += read
                                if (entryBytes > MAX_SINGLE_FILE_BYTES) {
                                    destinationDir.deleteRecursively()
                                    return SandboxResult.Rejected(
                                        SandboxViolation.FILE_TOO_LARGE,
                                        "Entry ${entry.name} exceeds single-file cap",
                                    )
                                }
                                if (totalUncompressed > MAX_ARCHIVE_TOTAL_BYTES) {
                                    destinationDir.deleteRecursively()
                                    return SandboxResult.Rejected(
                                        SandboxViolation.ARCHIVE_BOMB_DETECTED,
                                        "Archive uncompressed payload exceeds sandbox limit",
                                    )
                                }
                                out.write(buffer, 0, read)
                            }
                        }
                        extractedFiles.add(safeDest)
                    }
                    zis.closeEntry()
                }
            }
            SandboxResult.Valid(extractedFiles)
        } catch (e: Exception) {
            destinationDir.deleteRecursively()
            SandboxResult.Rejected(SandboxViolation.IO_FAILURE, e.message ?: "Corrupted ZIP archive")
        }
    }

    fun sha256OfFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        file.inputStream().use { ins ->
            while (true) {
                val read = ins.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
