package com.ophoto

import android.app.Application
import android.content.Context
import com.ophoto.core.device.DeviceHardwareProfile
import com.ophoto.core.device.OppoDeviceInspector
import com.ophoto.data.importer.FilterImporterImpl
import com.ophoto.data.local.AppPreferences
import com.ophoto.data.local.CompositionGuideType
import com.ophoto.data.local.OphotoDatabase
import com.ophoto.data.repository.FilterRepository
import com.ophoto.data.repository.MediaRepository
import com.ophoto.engine.camera.CameraX2Engine
import com.ophoto.engine.camera.ICameraEngine
import com.ophoto.engine.filter.FilterEngineImpl
import com.ophoto.engine.filter.IFilterEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

data class FieldToCameraSetupCommand(
    val presetId: String,
    val compositionGuide: CompositionGuideType,
    val evOffset: Float,
    val kelvin: Int,
    val focalMmHint: Int,
    val tipBanner: String,
)

class OphotoContainer(appContext: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: OphotoDatabase = OphotoDatabase.getInstance(appContext)
    val preferences: AppPreferences = AppPreferences(appContext)
    val filterImporter: FilterImporterImpl = FilterImporterImpl(appContext)
    val filterRepository: FilterRepository = FilterRepository(database.filterDao(), filterImporter)
    val mediaRepository: MediaRepository = MediaRepository(
        database.captureRecordDao(),
        database.editHistoryDao(),
        database.fieldProgressDao(),
    )
    val cameraEngine: ICameraEngine = CameraX2Engine(appContext)
    val filterEngine: IFilterEngine = FilterEngineImpl()
    val hardwareProfile: DeviceHardwareProfile by lazy { OppoDeviceInspector.inspect(appContext) }

    private val _hardwareKeyEvents = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val hardwareKeyEvents: SharedFlow<Int> = _hardwareKeyEvents

    private val _fieldSetupCommands = MutableSharedFlow<FieldToCameraSetupCommand>(extraBufferCapacity = 2)
    val fieldSetupCommands: SharedFlow<FieldToCameraSetupCommand> = _fieldSetupCommands

    private val _editorMediaTarget = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val editorMediaTarget: SharedFlow<String> = _editorMediaTarget

    init {
        appScope.launch {
            filterRepository.ensureBuiltInsSeeded()
        }
    }

    fun emitVolumeKey(keyCode: Int) {
        _hardwareKeyEvents.tryEmit(keyCode)
    }

    fun applyFieldCardToCamera(command: FieldToCameraSetupCommand) {
        _fieldSetupCommands.tryEmit(command)
    }

    fun openMediaInEditor(uriString: String) {
        _editorMediaTarget.tryEmit(uriString)
    }
}

class OphotoApplication : Application() {
    lateinit var container: OphotoContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = OphotoContainer(this)
    }
}
