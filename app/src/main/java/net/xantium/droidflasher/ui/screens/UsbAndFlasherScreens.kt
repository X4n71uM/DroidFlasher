package net.xantium.droidflasher.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import net.xantium.droidflasher.R
import net.xantium.droidflasher.data.local.SavedPitProfileEntity
import net.xantium.droidflasher.model.FlashJobItem
import net.xantium.droidflasher.model.FlashJobStatus
import net.xantium.droidflasher.model.FlashProtocol
import net.xantium.droidflasher.model.PitValidationResult
import net.xantium.droidflasher.model.SafetyConfig
import net.xantium.droidflasher.model.UsbTargetDevice
import net.xantium.droidflasher.ui.theme.AmberCaution
import net.xantium.droidflasher.ui.theme.BenchCardBorder
import net.xantium.droidflasher.ui.theme.BenchSurface
import net.xantium.droidflasher.ui.theme.BenchSurfaceVariant
import net.xantium.droidflasher.ui.theme.CyanTelemetry
import net.xantium.droidflasher.ui.theme.HazardCrimson
import net.xantium.droidflasher.ui.theme.JetBrainsMonoFontFamily
import net.xantium.droidflasher.ui.theme.SignalGreen
import net.xantium.droidflasher.ui.theme.TextSecondary

@Composable
fun UsbDeviceInspectorScreen(
    availableDevices: List<UsbTargetDevice>,
    selectedDevice: UsbTargetDevice?,
    pitValidation: PitValidationResult?,
    onScanUsb: () -> Unit,
    onSelectDevice: (UsbTargetDevice) -> Unit,
    onSelectProtocol: (FlashProtocol) -> Unit,
    onRequestHandshake: () -> Unit,
    onPushFileToTarget: () -> Unit,
    onPullPitFromDevice: () -> Unit,
    onPushPitToDevice: () -> Unit = {},
    onShowTooltip: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // Quick-Access Action Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onScanUsb,
                    modifier = Modifier.testTag("scan_usb_otg_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan USB OTG")
                }

                Button(
                    onClick = onRequestHandshake,
                    modifier = Modifier.testTag("handshake_protocol_button")
                ) {
                    Icon(Icons.Default.SyncAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Handshake")
                }

                OutlinedButton(
                    onClick = onPullPitFromDevice,
                    modifier = Modifier.testTag("pull_device_pit_button")
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pull PIT")
                }

                OutlinedButton(
                    onClick = onPushPitToDevice,
                    modifier = Modifier.testTag("push_device_pit_button")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Push PIT")
                }

                OutlinedButton(
                    onClick = onPushFileToTarget,
                    modifier = Modifier.testTag("push_file_target_button")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Push File")
                }
            }
        }

        // Protocol Selector Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BenchSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BenchCardBorder, RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "COMMUNICATION PROTOCOL BACKEND",
                            style = MaterialTheme.typography.labelLarge,
                            color = CyanTelemetry
                        )
                        IconButton(
                            onClick = {
                                onShowTooltip("Select LOKE for Samsung Download Mode (Heimdall), Fastboot for standard Android bootloaders, or ADB Push for SYNC file transfers and recovery sideload.")
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Protocol Info",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FlashProtocol.entries.forEach { proto ->
                            val isSelected = selectedDevice?.protocol == proto
                            FilterChip(
                                selected = isSelected,
                                onClick = { onSelectProtocol(proto) },
                                label = { Text(proto.label) },
                                modifier = Modifier.testTag("protocol_chip_${proto.shortCode}")
                            )
                        }
                    }
                }
            }
        }

        // Connected Target Selector
        item {
            Text(
                text = "DETECTED USB OTG HARDWARE TARGETS (${availableDevices.size})",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
        }

        if (availableDevices.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BenchSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BenchCardBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BenchSurfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Usb,
                                contentDescription = null,
                                tint = CyanTelemetry,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "No USB OTG Device Connected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Connect a target Android smartphone via USB-C OTG cable or adapter in Download Mode (Odin/LOKE), Fastboot, or ADB Recovery.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        OutlinedButton(
                            onClick = onScanUsb,
                            modifier = Modifier.testTag("scan_usb_empty_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Scan USB OTG Bus")
                        }
                    }
                }
            }
        }

        items(availableDevices, key = { it.deviceId }) { dev ->
            val isSelected = selectedDevice?.deviceId == dev.deviceId
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) BenchSurfaceVariant else BenchSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) CyanTelemetry else BenchCardBorder,
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onSelectDevice(dev) }
                    .testTag("usb_device_card_${dev.modelCode}")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Usb,
                                contentDescription = null,
                                tint = if (isSelected) CyanTelemetry else TextSecondary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = dev.productName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Surface(
                            color = Color(0xFF06331D),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "HARDWARE USB OTG",
                                style = MaterialTheme.typography.labelSmall,
                                color = SignalGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "MODEL: ${dev.modelCode} • VID:PID ${dev.formattedVidPid}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = CyanTelemetry
                        )
                        Text(
                            text = "SN: ${dev.serialNumber}",
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = TextSecondary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "IN: ${dev.bulkInAddress} | OUT: ${dev.bulkOutAddress}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = TextSecondary
                        )
                        Text(
                            text = if (dev.handshakeVerified) "ACCESS: ACCEPTED & SYNCED" else "ACCESS: STANDBY",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (dev.handshakeVerified) SignalGreen else AmberCaution
                        )
                    }
                }
            }
        }

        // Active Device Hardware Telemetry Summary
        if (selectedDevice != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BenchSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BenchCardBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ACTIVE TARGET TELEMETRY & ACCESS STATUS",
                            style = MaterialTheme.typography.labelLarge,
                            color = CyanTelemetry
                        )
                        TelemetryRow("Host Package Identity", "net.xantium.droidflasher (DroidFlasher)")
                        TelemetryRow("USB Permission & DTR/RTS", if (selectedDevice.hasUsbPermission) "GRANTED (CDC 0x22 DTR/RTS Active)" else "PENDING PERMISSION")
                        TelemetryRow("Bootloader State", selectedDevice.bootloaderStatus)
                        TelemetryRow("Flash Storage", selectedDevice.storageType)
                        TelemetryRow("Max Bulk Packet", "${selectedDevice.maxPacketSize} Bytes (${selectedDevice.interfaceCount} USB Interfaces)")
                        TelemetryRow(
                            "Loaded PIT Layout",
                            pitValidation?.let {
                                "${it.parsedEntries.size} Partitions (${String.format(Locale.US, "%.1f GB", it.totalAllocatedMB / 1024.0)}) • Magic 0x${it.header.magic.toString(16).uppercase()}"
                            } ?: "Not loaded"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = JetBrainsMonoFontFamily,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun SelectiveFlasherScreen(
    flashQueue: List<FlashJobItem>,
    safetyConfig: SafetyConfig,
    isFlashingBatch: Boolean,
    batchProgress: Float,
    savedProfiles: List<SavedPitProfileEntity> = emptyList(),
    onUpdateSafetyConfig: ((SafetyConfig) -> SafetyConfig) -> Unit,
    onTogglePartitionSelect: (Int) -> Unit,
    onSelectAllPartitions: (Boolean) -> Unit,
    onStageStockBundle: () -> Unit = {},
    onPickCustomPayloadForPartition: (Int) -> Unit,
    onExecuteFlashSequence: () -> Unit,
    onLoadSavedProfile: (SavedPitProfileEntity) -> Unit = {},
    onShowTooltip: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedCount = flashQueue.count { it.isSelected }
    val readyCount = flashQueue.count { it.isSelected && it.payloadFilename.isNotBlank() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Saved & Pulled PIT Profiles Quick-Selector
        if (savedProfiles.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BenchSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BenchCardBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = CyanTelemetry,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "SAVED PIT PROFILES (${savedProfiles.size})",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CyanTelemetry
                                )
                            }
                            IconButton(
                                onClick = {
                                    onShowTooltip("Saved or pulled PIT tables from connected devices are stored in Room DB and Downloads. Tap any profile to instantly stage its partitions into this Selective Flasher queue.")
                                },
                                modifier = Modifier.size(26.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Profile Help",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            savedProfiles.forEach { profile ->
                                FilterChip(
                                    selected = false,
                                    onClick = { onLoadSavedProfile(profile) },
                                    label = {
                                        Column {
                                            Text(profile.profileName, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                "${profile.deviceModel} • ${profile.entryCount} parts",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = null,
                                            tint = SignalGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("flasher_saved_profile_${profile.id}")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Configurable Workshop Pre-Flash Safety Suite
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BenchSurface),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BenchCardBorder, RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = CyanTelemetry,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PRE-FLASH WORKSHOP SAFETY GATES (OPTIONAL)",
                                style = MaterialTheme.typography.labelLarge,
                                color = CyanTelemetry
                            )
                        }
                        IconButton(
                            onClick = {
                                onShowTooltip("All safety checks are user-toggleable. Enable SHA-256 verification and alignment checks to prevent corrupt writes, or toggle Full Repartition when rewriting PIT offsets.")
                            },
                            modifier = Modifier.size(26.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Safety Gate Help",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = safetyConfig.verifyChecksumBeforeFlash,
                            onClick = {
                                onUpdateSafetyConfig { it.copy(verifyChecksumBeforeFlash = !it.verifyChecksumBeforeFlash) }
                            },
                            label = { Text("SHA-256 / MD5 Check") },
                            modifier = Modifier.testTag("safety_chip_checksum")
                        )
                        FilterChip(
                            selected = safetyConfig.checkPartitionAlignment,
                            onClick = {
                                onUpdateSafetyConfig { it.copy(checkPartitionAlignment = !it.checkPartitionAlignment) }
                            },
                            label = { Text("PIT Alignment Check") },
                            modifier = Modifier.testTag("safety_chip_alignment")
                        )
                        FilterChip(
                            selected = safetyConfig.requireEfsBackupBeforeRepartition,
                            onClick = {
                                onUpdateSafetyConfig { it.copy(requireEfsBackupBeforeRepartition = !it.requireEfsBackupBeforeRepartition) }
                            },
                            label = { Text("Require EFS Backup") },
                            modifier = Modifier.testTag("safety_chip_efs_guard")
                        )
                        FilterChip(
                            selected = safetyConfig.dryRunHandshakeBeforeBatch,
                            onClick = {
                                onUpdateSafetyConfig { it.copy(dryRunHandshakeBeforeBatch = !it.dryRunHandshakeBeforeBatch) }
                            },
                            label = { Text("Dry-Run Handshake") },
                            modifier = Modifier.testTag("safety_chip_dry_run")
                        )
                        FilterChip(
                            selected = safetyConfig.fullRepartitionEnabled,
                            onClick = {
                                onUpdateSafetyConfig { it.copy(fullRepartitionEnabled = !it.fullRepartitionEnabled) }
                            },
                            label = {
                                Text(
                                    text = "Full Repartition (PIT Write)",
                                    color = if (safetyConfig.fullRepartitionEnabled) HazardCrimson else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            modifier = Modifier.testTag("safety_chip_repartition")
                        )
                        FilterChip(
                            selected = safetyConfig.autoRebootAfterFlash,
                            onClick = {
                                onUpdateSafetyConfig { it.copy(autoRebootAfterFlash = !it.autoRebootAfterFlash) }
                            },
                            label = { Text("Auto-Reboot Target") },
                            modifier = Modifier.testTag("safety_chip_autoreboot")
                        )
                    }
                }
            }
        }

        // Batch Actions & Progress Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BenchSurfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BenchCardBorder, RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SELECTIVE PARTITION FLASH QUEUE",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$selectedCount of ${flashQueue.size} partitions selected • $readyCount file(s) mapped",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSelectAllPartitions(selectedCount < flashQueue.size) },
                            enabled = !isFlashingBatch && flashQueue.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("toggle_select_all_partitions_button")
                        ) {
                            Text(if (selectedCount < flashQueue.size) "Select All" else "Clear Selection")
                        }

                        Button(
                            onClick = onExecuteFlashSequence,
                            enabled = !isFlashingBatch && selectedCount > 0,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (safetyConfig.fullRepartitionEnabled) HazardCrimson else CyanTelemetry
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("execute_flash_sequence_button")
                        ) {
                            Icon(
                                imageVector = if (safetyConfig.fullRepartitionEnabled) Icons.Default.Bolt else Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isFlashingBatch) "Flashing..." else "Flash Selected ($selectedCount)"
                            )
                        }
                    }

                    if (isFlashingBatch || batchProgress > 0f) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (isFlashingBatch) "BULK USB TRANSFER IN PROGRESS" else "LAST BATCH SEQUENCE COMPLETE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyanTelemetry
                                )
                                Text(
                                    text = "${(batchProgress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SignalGreen
                                )
                            }
                            LinearProgressIndicator(
                                progress = { batchProgress },
                                color = CyanTelemetry,
                                trackColor = BenchSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }
        }

        if (flashQueue.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BenchSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BenchCardBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "No Partitions in Flasher Queue",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Connect a target device in the USB & Link tab and tap 'Pull PIT', open a .pit file in the PIT Editor, or select a saved profile below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        if (savedProfiles.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "SAVED PIT PROFILES AVAILABLE TO FLASH:",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanTelemetry
                            )
                            savedProfiles.forEach { prof ->
                                OutlinedButton(
                                    onClick = { onLoadSavedProfile(prof) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = null,
                                        tint = SignalGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Stage ${prof.profileName} (${prof.entryCount} parts)")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Writable Partitions List parsed from PIT
        items(flashQueue, key = { it.partitionEntry.identifier }) { item ->
            val entry = item.partitionEntry
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (item.isSelected) BenchSurfaceVariant else BenchSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = when {
                            item.status == FlashJobStatus.VERIFIED_OK -> SignalGreen.copy(alpha = 0.7f)
                            item.isSelected -> CyanTelemetry.copy(alpha = 0.6f)
                            else -> BenchCardBorder
                        },
                        shape = RoundedCornerShape(10.dp)
                    )
                    .testTag("flash_partition_row_${entry.partitionName}")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = item.isSelected,
                            onCheckedChange = { onTogglePartitionSelect(entry.identifier) },
                            modifier = Modifier.testTag("checkbox_partition_${entry.partitionName}")
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = entry.partitionName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ID:${entry.identifier} • ${String.format(Locale.US, "%.1f MB", entry.sizeMB)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                                if (entry.isCriticalRadio) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = Color(0xFF332400),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "CRITICAL RADIO/IMEI",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = AmberCaution,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "LBA Offset: 0x${entry.blockOffset.toString(16).uppercase()} (${entry.blockCount} blocks • ${entry.attributes}/${entry.updateAttributes})",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = TextSecondary
                            )
                        }

                        OutlinedButton(
                            onClick = { onPickCustomPayloadForPartition(entry.identifier) },
                            modifier = Modifier.testTag("map_file_button_${entry.partitionName}")
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (item.payloadFilename.isNotBlank()) item.payloadFilename else entry.flashFilename,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (item.payloadFilename.isNotBlank() || item.status != FlashJobStatus.IDLE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when (item.status) {
                                        FlashJobStatus.VERIFIED_OK -> Icons.Default.CheckCircle
                                        FlashJobStatus.FAILED -> Icons.Default.Warning
                                        else -> Icons.Default.Verified
                                    },
                                    contentDescription = null,
                                    tint = when (item.status) {
                                        FlashJobStatus.VERIFIED_OK -> SignalGreen
                                        FlashJobStatus.FAILED -> HazardCrimson
                                        else -> CyanTelemetry
                                    },
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = item.statusDetail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (item.bytesTransferred > 0L) {
                                Text(
                                    text = "${item.bytesTransferred / 1024} KB written",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = SignalGreen
                                )
                            }
                        }
                    }

                    if (item.status == FlashJobStatus.FLASHING || item.progress > 0f) {
                        LinearProgressIndicator(
                            progress = { item.progress },
                            color = if (item.status == FlashJobStatus.VERIFIED_OK) SignalGreen else CyanTelemetry,
                            trackColor = BenchSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
    }
}
