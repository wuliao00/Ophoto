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
import com.ophoto.ui.camera.ViewfinderHost
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

    /** 取景会话共享宿主：跨页面切换时保留预览 View，消除二次绑定黑场。 */
    val viewfinderHost: ViewfinderHost = ViewfinderHost()

    private val _hardwareKeyEvents = MutableSharedFlow<Int>(extraBufferCapacity = 4)
    val hardwareKeyEvents: SharedFlow<Int> = _hardwareKeyEvents

    private val _fieldSetupCommands = MutableSharedFlow<FieldToCameraSetupCommand>(extraBufferCapacity = 2)
    val fieldSetupCommands: SharedFlow<FieldToCameraSetupCommand> = _fieldSetupCommands

    private val _editorMediaTarget = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val editorMediaTarget: SharedFlow<String> = _editorMediaTarget

    /**
     * 最近一次「应当被暗房打开」的成片。SharedFlow 没有重放，
     * 所以拍照后即使暗房页面还没被创建（或已被切走销毁），
     * 暗房首次组合时也能凭这个值直接显示刚拍的这张照片。
     */
    @Volatile
    var lastCapturedMediaUri: String? = null
        private set

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
        lastCapturedMediaUri = uriString
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
