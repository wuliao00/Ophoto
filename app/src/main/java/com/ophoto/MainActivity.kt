package com.ophoto

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.lifecycleScope
import com.ophoto.data.importer.ImportResult
import com.ophoto.ui.navigation.OphotoAppShell
import com.ophoto.ui.theme.OphotoTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container: OphotoContainer
        get() = (application as OphotoApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        handleIncomingFileIntent(intent)

        setContent {
            val screenWidthDp = LocalConfiguration.current.screenWidthDp
            val isDualPane = screenWidthDp >= 640
            OphotoTheme {
                OphotoAppShell(
                    container = container,
                    isExpandedDualPane = isDualPane,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingFileIntent(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            container.emitVolumeKey(keyCode)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleIncomingFileIntent(incoming: Intent?) {
        if (incoming?.action != Intent.ACTION_VIEW) return
        val uri = incoming.data ?: return
        lifecycleScope.launch {
            when (val result = container.filterImporter.importFromUri(uri)) {
                is ImportResult.Success -> {
                    container.filterRepository.savePresets(result.importedBatch, "shared_file")
                    Toast.makeText(
                        this@MainActivity,
                        "已导入滤镜：${result.filter.name}",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                is ImportResult.Error -> {
                    Toast.makeText(this@MainActivity, result.detail, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
