package com.ophoto.core.log

import java.time.Instant
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Privacy-preserving ring buffer logger (Doc §15):
 * Never persists raw photo pixels, user MediaStore paths, or GPS coordinates.
 */
object SafeLogger {

    private const val MAX_ENTRIES = 120
    private val ringBuffer = ConcurrentLinkedDeque<String>()

    fun info(tag: String, message: String) {
        append("INFO", tag, message)
    }

    fun warn(tag: String, message: String) {
        append("WARN", tag, message)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        val suffix = throwable?.javaClass?.simpleName?.let { " [$it]" }.orEmpty()
        append("ERR", tag, "$message$suffix")
    }

    private fun append(level: String, tag: String, message: String) {
        val sanitized = message
            .replace(Regex("content://[^\\s]+"), "content://<redacted>")
            .replace(Regex("/storage/[^\\s]+"), "/storage/<redacted>")
        val line = "${Instant.now()} [$level/$tag] $sanitized"
        ringBuffer.addLast(line)
        while (ringBuffer.size > MAX_ENTRIES) {
            ringBuffer.pollFirst()
        }
    }

    fun snapshot(): List<String> = ringBuffer.toList()

    fun clear() {
        ringBuffer.clear()
    }
}
