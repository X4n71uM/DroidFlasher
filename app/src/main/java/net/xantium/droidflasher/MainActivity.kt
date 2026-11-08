package net.xantium.droidflasher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.xantium.droidflasher.data.UsbOtgEngine
import net.xantium.droidflasher.model.LogLevel
import net.xantium.droidflasher.model.WorkbenchTab
import net.xantium.droidflasher.ui.components.ContextualTooltipBanner
import net.xantium.droidflasher.ui.components.LiveTerminalLogDock
import net.xantium.droidflasher.ui.components.RepartitionHazardModal
import net.xantium.droidflasher.ui.components.WorkshopHelperDrawerContent
import net.xantium.droidflasher.ui.components.WorkshopTopStatusBar
import net.xantium.droidflasher.ui.screens.BackupStudioScreen
import net.xantium.droidflasher.ui.screens.ProfessionalPitEditorScreen
import net.xantium.droidflasher.ui.screens.SelectiveFlasherScreen
import net.xantium.droidflasher.ui.screens.UsbDeviceInspectorScreen
import net.xantium.droidflasher.ui.theme.BenchSurface
import net.xantium.droidflasher.ui.theme.DroidFlasherTheme
import net.xantium.droidflasher.viewmodel.WorkbenchViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: WorkbenchViewModel by viewModels()

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbOtgEngine.ACTION_USB_PERMISSION -> {
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    viewModel.onUsbPermissionResult(granted)
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    viewModel.appendLog(
                        LogLevel.INFO,
                        "USB-HOTPLUG",
                        "Physical USB OTG target attached to host port.",
                        ""
                    )
                    viewModel.refreshUsbDeviceList()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    viewModel.appendLog(
                        LogLevel.WARN,
                        "USB-DETACH",
                        "Physical USB OTG target disconnected from host port.",
                        ""
                    )
                    viewModel.refreshUsbDeviceList()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val filter = IntentFilter().apply {
            addAction(UsbOtgEngine.ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            this,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        setContent {
            DroidFlasherTheme {
                DroidFlasherWorkbenchApp(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(usbReceiver) }
    }
}

@Composable
fun DroidFlasherWorkbenchApp(viewModel: WorkbenchViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val availableDevices by viewModel.availableDevices.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val pitText by viewModel.pitEditorText.collectAsStateWithLifecycle()
    val pitValidation by viewModel.pitValidation.collectAsStateWithLifecycle()
    val isVisualPitMode by viewModel.isVisualPitMode.collectAsStateWithLifecycle()
    val pitSearchQuery by viewModel.pitSearchQuery.collectAsStateWithLifecycle()
    val pitReplaceText by viewModel.pitReplaceText.collectAsStateWithLifecycle()
    val flashQueue by viewModel.flashQueue.collectAsStateWithLifecycle()
    val isFlashingBatch by viewModel.isFlashingBatch.collectAsStateWithLifecycle()
    val batchProgress by viewModel.batchProgress.collectAsStateWithLifecycle()
    val safetyConfig by viewModel.safetyConfig.collectAsStateWithLifecycle()
    val hasRecentEfsBackup by viewModel.hasRecentEfsBackup.collectAsStateWithLifecycle()
    val showHazardModal by viewModel.showRepartitionHazardModal.collectAsStateWithLifecycle()
    val selectedBackupIds by viewModel.selectedBackupIds.collectAsStateWithLifecycle()
    val backupProgress by viewModel.backupProgress.collectAsStateWithLifecycle()
    val backupSessions by viewModel.backupSessions.collectAsStateWithLifecycle()
    val savedPitProfiles by viewModel.savedPitProfiles.collectAsStateWithLifecycle()
    val terminalLogs by viewModel.terminalLogs.collectAsStateWithLifecycle()
    val terminalFilter by viewModel.terminalFilter.collectAsStateWithLifecycle()
    val isTerminalExpanded by viewModel.isTerminalExpanded.collectAsStateWithLifecycle()
    val activeTooltip by viewModel.activeTooltip.collectAsStateWithLifecycle()

    var pendingPayloadPartitionId by remember { mutableIntStateOf(-1) }

    val payloadFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && pendingPayloadPartitionId >= 0) {
            val filename = resolveDisplayName(context, uri) ?: "custom_payload.img"
            viewModel.assignCustomPayloadUriToPartition(pendingPayloadPartitionId, uri, filename)
        }
    }

    val pushFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val filename = if (uri != null) resolveDisplayName(context, uri) ?: "pushed_payload.bin" else "droidflasher_probe.bin"
        viewModel.pushFileToConnectedDevice(uri, filename)
    }

    val pitFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.loadPitFromUri(uri)
        }
    }

    BackHandler(enabled = drawerState.isOpen || isTerminalExpanded || activeTab != WorkbenchTab.USB_DEVICE) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            isTerminalExpanded -> viewModel.setTerminalExpanded(false)
            activeTab != WorkbenchTab.USB_DEVICE -> viewModel.selectTab(WorkbenchTab.USB_DEVICE)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            WorkshopHelperDrawerContent(
                topics = viewModel.guideTopics,
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        }
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideScreen = maxWidth >= 680.dp

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                topBar = {
                    WorkshopTopStatusBar(
                        selectedDevice = selectedDevice,
                        onOpenGuideDrawer = { scope.launch { drawerState.open() } }
                    )
                },
                bottomBar = {
                    Surface(
                        color = BenchSurface,
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                        ) {
                            LiveTerminalLogDock(
                                logs = terminalLogs,
                                activeFilter = terminalFilter,
                                isExpanded = isTerminalExpanded,
                                onToggleExpand = { viewModel.setTerminalExpanded(!isTerminalExpanded) },
                                onSelectFilter = viewModel::setTerminalFilter,
                                onClearLogs = viewModel::clearTerminalLogs,
                                onExportLogs = viewModel::exportTerminalLogsToDownloads
                            )
                            if (!isWideScreen) {
                                NavigationBar(
                                    containerColor = BenchSurface,
                                    windowInsets = WindowInsets(0, 0, 0, 0)
                                ) {
                                    WorkbenchTab.entries.forEach { tab ->
                                        NavigationBarItem(
                                            selected = activeTab == tab,
                                            onClick = { viewModel.selectTab(tab) },
                                            icon = {
                                                Icon(
                                                    imageVector = tab.toIcon(),
                                                    contentDescription = tab.title
                                                )
                                            },
                                            label = { Text(tab.title) },
                                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (isWideScreen) {
                        NavigationRail(
                            containerColor = BenchSurface,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            WorkbenchTab.entries.forEach { tab ->
                                NavigationRailItem(
                                    selected = activeTab == tab,
                                    onClick = { viewModel.selectTab(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = tab.toIcon(),
                                            contentDescription = tab.title
                                        )
                                    },
                                    label = { Text(tab.title) },
                                    modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        ContextualTooltipBanner(
                            tooltipText = activeTooltip,
                            onDismiss = { viewModel.showTooltip(null) }
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            when (activeTab) {
                                WorkbenchTab.USB_DEVICE -> UsbDeviceInspectorScreen(
                                    availableDevices = availableDevices,
                                    selectedDevice = selectedDevice,
                                    pitValidation = pitValidation,
                                    onScanUsb = viewModel::refreshUsbDeviceList,
                                    onSelectDevice = viewModel::selectTargetDevice,
                                    onSelectProtocol = viewModel::setTargetProtocol,
                                    onRequestHandshake = viewModel::requestUsbPermissionOrHandshake,
                                    onPushFileToTarget = {
                                        pushFileLauncher.launch(arrayOf("*/*"))
                                    },
                                    onPullPitFromDevice = viewModel::pullPitFromConnectedDevice,
                                    onPushPitToDevice = viewModel::pushCurrentPitToConnectedDevice,
                                    onShowTooltip = viewModel::showTooltip
                                )

                                WorkbenchTab.FLASHER -> SelectiveFlasherScreen(
                                    flashQueue = flashQueue,
                                    safetyConfig = safetyConfig,
                                    isFlashingBatch = isFlashingBatch,
                                    batchProgress = batchProgress,
                                    savedProfiles = savedPitProfiles,
                                    onUpdateSafetyConfig = viewModel::updateSafetyConfig,
                                    onTogglePartitionSelect = viewModel::toggleFlashPartitionSelection,
                                    onSelectAllPartitions = viewModel::selectAllFlashPartitions,
                                    onPickCustomPayloadForPartition = { partId ->
                                        pendingPayloadPartitionId = partId
                                        payloadFileLauncher.launch(arrayOf("*/*"))
                                    },
                                    onExecuteFlashSequence = viewModel::executeSelectiveFlashSequence,
                                    onLoadSavedProfile = viewModel::loadSavedPitProfile,
                                    onShowTooltip = viewModel::showTooltip
                                )

                                WorkbenchTab.BACKUP -> BackupStudioScreen(
                                    deviceModel = selectedDevice?.modelCode ?: "SM-S918B",
                                    partitions = pitValidation?.parsedEntries.orEmpty(),
                                    selectedBackupIds = selectedBackupIds,
                                    backupProgress = backupProgress,
                                    backupSessions = backupSessions,
                                    onToggleBackupPartition = viewModel::toggleBackupPartition,
                                    onSelectBackupPreset = viewModel::selectBackupPreset,
                                    onStartBackup = { viewModel.executeAutomatedBackup(false) },
                                    onStageBackupForRestore = viewModel::stageBackupSessionIntoFlasher,
                                    onDeleteBackupSession = viewModel::deleteBackupSession
                                )

                                WorkbenchTab.PIT_EDITOR -> ProfessionalPitEditorScreen(
                                    rawPitText = pitText,
                                    pitValidation = pitValidation,
                                    isVisualMode = isVisualPitMode,
                                    searchQuery = pitSearchQuery,
                                    replaceText = pitReplaceText,
                                    savedProfiles = savedPitProfiles,
                                    onUpdatePitText = { viewModel.updatePitEditorText(it, false) },
                                    onSetVisualMode = viewModel::setVisualPitMode,
                                    onUpdateSearchQuery = viewModel::updatePitSearchQuery,
                                    onUpdateReplaceText = viewModel::updatePitReplaceText,
                                    onExecuteSearchReplace = { viewModel.executePitSearchAndReplace() },
                                    onUpdateVisualEntry = viewModel::updateVisualPartitionEntry,
                                    onAddPartition = viewModel::addNewPartitionToPit,
                                    onLoadExternalPitFile = {
                                        pitFileLauncher.launch(arrayOf("*/*"))
                                    },
                                    onPullPitFromDevice = viewModel::pullPitFromConnectedDevice,
                                    onPushPitToDevice = viewModel::pushCurrentPitToConnectedDevice,
                                    onExportBinaryPit = viewModel::exportBinaryPitToDownloads,
                                    onSavePitProfile = viewModel::saveCurrentPitProfileToRoom,
                                    onLoadSavedProfile = viewModel::loadSavedPitProfile,
                                    onDeleteSavedProfile = viewModel::deleteSavedPitProfile
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHazardModal) {
        RepartitionHazardModal(
            hasRecentEfsBackup = hasRecentEfsBackup,
            onBackupEfsNow = {
                viewModel.dismissRepartitionHazardModal(false)
                viewModel.selectTab(WorkbenchTab.BACKUP)
                viewModel.executeAutomatedBackup(onlyCriticalEfs = true)
            },
            onConfirmRepartition = {
                viewModel.dismissRepartitionHazardModal(true)
            },
            onCancel = {
                viewModel.dismissRepartitionHazardModal(false)
            }
        )
    }
}

private fun WorkbenchTab.toIcon() = when (this) {
    WorkbenchTab.USB_DEVICE -> Icons.Default.Usb
    WorkbenchTab.FLASHER -> Icons.Default.FlashOn
    WorkbenchTab.BACKUP -> Icons.Default.Backup
    WorkbenchTab.PIT_EDITOR -> Icons.Default.Code
}

private fun resolveDisplayName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
        }
    }.getOrNull()
}
