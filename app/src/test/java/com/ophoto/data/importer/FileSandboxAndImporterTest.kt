package com.ophoto.data.importer

import com.ophoto.core.security.FileSandboxValidator
import com.ophoto.core.security.SandboxResult
import com.ophoto.core.security.SandboxViolation
import com.ophoto.engine.filter.CubeLutParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileSandboxAndImporterTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `sandbox blocks zip-slip traversal and executable scripts`() {
        val root = tempFolder.newFolder("sandbox")
        val traversal = FileSandboxValidator.validateEntryPath("../../etc/passwd", root)
        assertTrue(traversal is SandboxResult.Rejected)
        assertEquals(SandboxViolation.PATH_TRAVERSAL, (traversal as SandboxResult.Rejected).reason)

        val scriptCheck = FileSandboxValidator.validateEntryPath("payload/run.sh", root)
        assertTrue(scriptCheck is SandboxResult.Rejected)
        assertEquals(
            SandboxViolation.FORBIDDEN_EXECUTABLE_OR_SCRIPT,
            (scriptCheck as SandboxResult.Rejected).reason,
        )
    }

    @Test
    fun `imports complete filterpack zip with manifest and cube LUT`() = runTest {
        val storeDir = tempFolder.newFolder("filters")
        val importer = FilterImporterImpl(storageRoot = storeDir, currentAppVersion = "1.0.0")

        val lutText = CubeLutParser.serialize(
            CubeLutParser.buildAnalyticalLut("Film Green Pack", size = 3) { r, g, b, out ->
                out[0] = r * 0.9f
                out[1] = g
                out[2] = b * 0.95f
            },
        )
        val manifestJson = """
            {
              "id": "film_green_pack_test",
              "name": "青绿胶片测试包",
              "version": "1.0.0",
              "author": "Ophoto Test",
              "description": "Doc §4 标准滤镜包结构验证",
              "type": "lut_plus_params",
              "lut": "lut.cube",
              "params": {
                "contrast": -8,
                "saturation": -12,
                "temperature": -5,
                "tint": 8,
                "highlight_tint": "#d8f0e6",
                "shadow_tint": "#12332b",
                "grain_strength": 18,
                "vignette": 22,
                "fade": 10
              },
              "compatible": {
                "min_app_version": "1.0.0",
                "support_photo": true
              }
            }
        """.trimIndent()

        val packFile = File(tempFolder.root, "film-green.filterpack")
        ZipOutputStream(packFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write(manifestJson.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("lut.cube"))
            zos.write(lutText.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val res = importer.importFromZip(packFile)
        assertTrue("Expected ImportResult.Success, got $res", res is ImportResult.Success)
        val preset = (res as ImportResult.Success).filter
        assertEquals("青绿胶片测试包", preset.name)
        assertEquals(-8, preset.params.contrast)
        assertEquals(22, preset.params.vignette)
        assertTrue(preset.lutPath != null && File(preset.lutPath!!).isFile)
    }

    @Test
    fun `supports OPHOTO1 share code roundtrip and community master JSON format`() = runTest {
        val storeDir = tempFolder.newFolder("filters2")
        val importer = FilterImporterImpl(storageRoot = storeDir, currentAppVersion = "1.0.0")

        val communityJson = """
            {
              "version": 2,
              "name": "社区大师合集",
              "author": "@Photographer",
              "presets": [
                {
                  "name": "街头暖金人文",
                  "sections": [
                    {
                      "title": "color",
                      "items": [
                        { "label": "param_filter", "value": "暖阳 90%" },
                        { "label": "param_tone_curve", "value": "-25" },
                        { "label": "param_saturation", "value": "+12" },
                        { "label": "param_warm_cool", "value": "+14" },
                        { "label": "param_vignette", "value": "开" }
                      ]
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val imported = importer.importFromTextPayload(communityJson)
        assertTrue(imported is ImportResult.Success)
        val p = (imported as ImportResult.Success).filter
        assertEquals("街头暖金人文", p.name)
        assertEquals(-25, p.masterReference.tone)
        assertEquals(12, p.masterReference.saturation)
        assertTrue(p.masterReference.vignetteEnabled)

        // Share code roundtrip
        val code = FilterImporterImpl.exportPresetToJson(p, asShareCode = true)
        assertTrue(code.startsWith("OPHOTO1:"))
        val decoded = importer.importFromTextPayload(code)
        assertTrue(decoded is ImportResult.Success)
        assertEquals("街头暖金人文", (decoded as ImportResult.Success).filter.name)
    }
}
