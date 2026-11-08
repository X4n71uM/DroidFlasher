package net.xantium.droidflasher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FindReplace
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.xantium.droidflasher.data.local.BackupSessionEntity
import net.xantium.droidflasher.data.local.SavedPitProfileEntity
import net.xantium.droidflasher.model.BackupProgressState
import net.xantium.droidflasher.model.PitPartitionEntry
import net.xantium.droidflasher.model.PitValidationResult
import net.xantium.droidflasher.model.ValidationSeverity
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
fun BackupStudioScreen(
    deviceModel: String,
    partitions: List<PitPartitionEntry>,
    selectedBackupIds: Set<Int>,
    backupProgress: BackupProgressState,
    backupSessions: List<BackupSessionEntity>,
    onToggleBackupPartition: (Int) -> Unit,
    onSelectBackupPreset: (String) -> Unit,
    onStartBackup: () -> Unit,
    onStageBackupForRestore: (BackupSessionEntity) -> Unit,
    onDeleteBackupSession: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val previewTimestamp = remember {
        SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
    }
    val sanitizedModel = deviceModel.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "SM-S918B" }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Backup,
                            contentDescription = null,
                            tint = CyanTelemetry
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AUTOMATED NAND PARTITION BACKUP SYSTEM",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Reads raw blocks from connected target device and exports reusable .img/.bin firmware layouts + binary/text PIT + SHA256SUMS.txt to:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    Surface(
                        color = Color(0xFF070A0F),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, CyanTelemetry.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = backupProgress.lastCompletedFolder.ifBlank {
                                "Downloads/Backup_${sanitizedModel}_${previewTimestamp}/"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = SignalGreen,
                            modifier = Modifier.padding(10.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedBackupIds.size == partitions.size && partitions.isNotEmpty(),
                            onClick = { onSelectBackupPreset("ALL") },
                            label = { Text("Full Device (${partitions.size})") },
                            modifier = Modifier.testTag("backup_preset_all")
                        )
                        FilterChip(
                            selected = false,
                            onClick = { onSelectBackupPreset("EFS_RADIO") },
                            label = { Text("Critical EFS / Radio Only") },
                            modifier = Modifier.testTag("backup_preset_efs")
                        )
                        FilterChip(
                            selected = false,
                            onClick = { onSelectBackupPreset("BOOT_KERN") },
                            label = { Text("Boot / Recovery / VBMeta") },
                            modifier = Modifier.testTag("backup_preset_boot")
                        )
                    }

                    Button(
                        onClick = onStartBackup,
                        enabled = !backupProgress.isRunning && partitions.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("start_automated_backup_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (backupProgress.isRunning) {
                                "Reading ${backupProgress.currentPartition} (${backupProgress.completedPartitions}/${backupProgress.totalPartitions})..."
                            } else {
                                "Export Backup (${selectedBackupIds.size} Partitions) to Downloads/"
                            }
                        )
                    }

                    if (backupProgress.isRunning || backupProgress.overallProgress > 0f) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            LinearProgressIndicator(
                                progress = { backupProgress.overallProgress },
                                color = SignalGreen,
                                trackColor = BenchSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Text(
                                text = "Exported ${backupProgress.bytesWritten / 1024} KB • ${backupProgress.exportedFiles.size} modular files written",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = CyanTelemetry
                            )
                        }
                    }
                }
            }
        }

        if (backupSessions.isNotEmpty()) {
            item {
                Text(
                    text = "STORED DOWNLOADS BACKUP ARCHIVES (${backupSessions.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = CyanTelemetry
                )
            }

            items(backupSessions, key = { it.id }) { session ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = BenchSurfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, SignalGreen.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = session.displayFolderPath,
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = SignalGreen,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onDeleteBackupSession(session.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Archive Record",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Text(
                            text = "Device: ${session.deviceModel} • ${session.partitionCount} partitions (${session.totalBytes / 1024} KB) • EFS Protected: ${if (session.includesEfsRadio) "YES" else "NO"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )

                        OutlinedButton(
                            onClick = { onStageBackupForRestore(session) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("restore_backup_session_${session.id}")
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stage Backup into Selective Flasher")
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = "SELECT PARTITIONS FOR READBACK (${selectedBackupIds.size}/${partitions.size})",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
        }

        if (partitions.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = BenchSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BenchCardBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No Device Partitions Loaded",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Connect a target device via USB OTG and pull the PIT layout, or open a .pit file in the PIT Editor.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        items(partitions, key = { it.identifier }) { entry ->
            val checked = entry.identifier in selectedBackupIds
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (checked) BenchSurfaceVariant else BenchSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BenchCardBorder, RoundedCornerShape(8.dp))
                    .clickable { onToggleBackupPartition(entry.identifier) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { onToggleBackupPartition(entry.identifier) }
                        )
                        Column {
                            Text(
                                text = "${entry.partitionName}  →  ${entry.flashFilename}",
                                style = MaterialTheme.typography.labelLarge,
                                fontFamily = JetBrainsMonoFontFamily
                            )
                            Text(
                                text = "Offset 0x${entry.blockOffset.toString(16).uppercase()} • ${entry.blockCount} blocks (${String.format(Locale.US, "%.1f MB", entry.sizeMB)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                    if (entry.isCriticalRadio) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Critical EFS Radio Partition",
                            tint = AmberCaution
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfessionalPitEditorScreen(
    rawPitText: String,
    pitValidation: PitValidationResult?,
    isVisualMode: Boolean,
    searchQuery: String,
    replaceText: String,
    savedProfiles: List<SavedPitProfileEntity>,
    onUpdatePitText: (String) -> Unit,
    onSetVisualMode: (Boolean) -> Unit,
    onUpdateSearchQuery: (String) -> Unit,
    onUpdateReplaceText: (String) -> Unit,
    onExecuteSearchReplace: () -> Unit,
    onUpdateVisualEntry: (PitPartitionEntry) -> Unit,
    onAddPartition: (String, String, Long, String) -> Unit,
    onLoadExternalPitFile: () -> Unit,
    onPullPitFromDevice: () -> Unit = {},
    onPushPitToDevice: () -> Unit = {},
    onExportBinaryPit: () -> Unit,
    onSavePitProfile: (String) -> Unit,
    onLoadSavedProfile: (SavedPitProfileEntity) -> Unit,
    onDeleteSavedProfile: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSearchReplaceBar by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<PitPartitionEntry?>(null) }
    var showAddPartitionDialog by remember { mutableStateOf(false) }
    var showSaveProfileDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !isVisualMode,
                                onClick = { onSetVisualMode(false) },
                                leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Raw Syntax + Lines") },
                                modifier = Modifier.testTag("pit_mode_raw_button")
                            )
                            FilterChip(
                                selected = isVisualMode,
                                onClick = { onSetVisualMode(true) },
                                leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Visual Inspector") },
                                modifier = Modifier.testTag("pit_mode_visual_button")
                            )
                        }

                        IconButton(
                            onClick = { showSearchReplaceBar = !showSearchReplaceBar },
                            modifier = Modifier.testTag("toggle_pit_search_replace_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FindReplace,
                                contentDescription = "Toggle Search and Replace",
                                tint = if (showSearchReplaceBar) CyanTelemetry else TextSecondary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPullPitFromDevice,
                            modifier = Modifier.testTag("pit_editor_pull_device_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pull PIT")
                        }
                        OutlinedButton(
                            onClick = onPushPitToDevice,
                            enabled = pitValidation?.isValid == true,
                            modifier = Modifier.testTag("pit_editor_push_device_button")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Push PIT")
                        }
                        OutlinedButton(
                            onClick = onLoadExternalPitFile,
                            modifier = Modifier.testTag("pit_load_file_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Load .pit")
                        }
                        OutlinedButton(
                            onClick = { showSaveProfileDialog = true },
                            modifier = Modifier.testTag("pit_save_profile_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Profile")
                        }
                        Button(
                            onClick = onExportBinaryPit,
                            enabled = pitValidation?.isValid == true,
                            modifier = Modifier.testTag("pit_export_binary_button")
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export Binary .pit")
                        }
                        OutlinedButton(
                            onClick = { showAddPartitionDialog = true },
                            modifier = Modifier.testTag("pit_add_partition_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Partition")
                        }
                    }

                    if (showSearchReplaceBar) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = onUpdateSearchQuery,
                                label = { Text("Search token") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pit_search_input")
                            )
                            OutlinedTextField(
                                value = replaceText,
                                onValueChange = onUpdateReplaceText,
                                label = { Text("Replace with") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("pit_replace_input")
                            )
                            Button(
                                onClick = onExecuteSearchReplace,
                                modifier = Modifier.testTag("pit_execute_replace_button")
                            ) {
                                Text("Replace")
                            }
                        }
                    }
                }
            }
        }

        if (pitValidation != null) {
            item {
                val borderColor = when {
                    !pitValidation.isValid -> HazardCrimson
                    pitValidation.issues.isNotEmpty() -> AmberCaution
                    else -> SignalGreen
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = BenchSurfaceVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when {
                                        !pitValidation.isValid -> Icons.Default.ErrorOutline
                                        pitValidation.issues.isNotEmpty() -> Icons.Default.WarningAmber
                                        else -> Icons.Default.Verified
                                    },
                                    contentDescription = null,
                                    tint = borderColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (pitValidation.isValid) {
                                        "PIT STRUCTURE VALID • ${pitValidation.parsedEntries.size} PARTITIONS"
                                    } else {
                                        "PIT VALIDATION FAILED (${pitValidation.issues.count { it.severity == ValidationSeverity.ERROR }} ERRORS)"
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = borderColor
                                )
                            }
                            Text(
                                text = "MAGIC: 0x${pitValidation.header.magic.toString(16).uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = if (pitValidation.magicValid) SignalGreen else HazardCrimson
                            )
                        }

                        pitValidation.issues.take(4).forEach { issue ->
                            Text(
                                text = "Line ${issue.lineNumber} [${issue.partitionName}]: ${issue.message}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = if (issue.severity == ValidationSeverity.ERROR) HazardCrimson else AmberCaution
                            )
                        }
                    }
                }
            }
        }

        if (!isVisualMode) {
            item {
                val lines = rawPitText.lines()
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF070A0F)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BenchCardBorder, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier
                                .padding(end = 10.dp)
                                .background(Color(0xFF0E131D), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            lines.forEachIndexed { idx, _ ->
                                Text(
                                    text = String.format(Locale.US, "%02d", idx + 1),
                                    style = TextStyle(
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 12.sp,
                                        lineHeight = 20.sp,
                                        color = TextSecondary
                                    )
                                )
                            }
                        }

                        BasicTextField(
                            value = rawPitText,
                            onValueChange = onUpdatePitText,
                            textStyle = TextStyle(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 20.sp,
                                color = Color(0xFFECF2F8)
                            ),
                            cursorBrush = SolidColor(CyanTelemetry),
                            modifier = Modifier
                                .width(780.dp)
                                .testTag("pit_raw_code_editor")
                        )
                    }
                }
            }
        } else {
            items(pitValidation?.parsedEntries.orEmpty(), key = { it.identifier }) { entry ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = BenchSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BenchCardBorder, RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ID ${entry.identifier} • ${entry.partitionName} (${entry.flashFilename})",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = CyanTelemetry
                            )
                            Text(
                                text = "Start LBA: 0x${entry.blockOffset.toString(16).uppercase()} • Blocks: ${entry.blockCount} (${String.format(Locale.US, "%.2f MB", entry.sizeMB)}) • Flags: ${entry.attributes}/${entry.updateAttributes}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = TextSecondary
                            )
                        }
                        IconButton(
                            onClick = { editingEntry = entry },
                            modifier = Modifier.testTag("edit_visual_pit_entry_${entry.partitionName}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Partition Offset and Flags",
                                tint = CyanTelemetry
                            )
                        }
                    }
                }
            }
        }

        if (savedProfiles.isNotEmpty()) {
            item {
                Text(
                    text = "SAVED DROIDFLASHER PIT PROFILES (${savedProfiles.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
            }
            items(savedProfiles, key = { it.id }) { prof ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = BenchSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, BenchCardBorder, RoundedCornerShape(10.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = prof.profileName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Model: ${prof.deviceModel} • ${prof.entryCount} partitions",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        Row {
                            OutlinedButton(onClick = { onLoadSavedProfile(prof) }) {
                                Text("Load")
                            }
                            IconButton(onClick = { onDeleteSavedProfile(prof.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Profile", tint = TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    editingEntry?.let { current ->
        var offsetHex by remember(current) { mutableStateOf("0x${current.blockOffset.toString(16).uppercase()}") }
        var blocksStr by remember(current) { mutableStateOf(current.blockCount.toString()) }
        var filenameStr by remember(current) { mutableStateOf(current.flashFilename) }
        var attrStr by remember(current) { mutableStateOf(current.attributes) }

        AlertDialog(
            onDismissRequest = { editingEntry = null },
            title = { Text("Edit Partition [${current.partitionName}]") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = offsetHex,
                        onValueChange = { offsetHex = it },
                        label = { Text("Block Offset (Hex LBA)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = blocksStr,
                        onValueChange = { blocksStr = it },
                        label = { Text("Block Count (4KB Blocks)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = filenameStr,
                        onValueChange = { filenameStr = it },
                        label = { Text("Flash Filename (.img/.bin)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = attrStr,
                        onValueChange = { attrStr = it },
                        label = { Text("Attributes (RW / RO / STL)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsedOffset = offsetHex.removePrefix("0x").removePrefix("0X")
                            .toLongOrNull(16) ?: current.blockOffset
                        val parsedBlocks = blocksStr.toLongOrNull() ?: current.blockCount
                        onUpdateVisualEntry(
                            current.copy(
                                blockOffset = parsedOffset,
                                blockCount = parsedBlocks,
                                flashFilename = filenameStr.ifBlank { current.flashFilename },
                                attributes = attrStr.uppercase(Locale.US).ifBlank { "RW" }
                            )
                        )
                        editingEntry = null
                    }
                ) {
                    Text("Apply to PIT")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { editingEntry = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddPartitionDialog) {
        var name by remember { mutableStateOf("CUSTOM_VENDOR") }
        var file by remember { mutableStateOf("custom_vendor.img") }
        var blocks by remember { mutableStateOf("32768") }
        var attr by remember { mutableStateOf("RW") }

        AlertDialog(
            onDismissRequest = { showAddPartitionDialog = false },
            title = { Text("Append New PIT Partition") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Partition Name (Max 31 ASCII)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = file,
                        onValueChange = { file = it },
                        label = { Text("Default Flash Filename") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = blocks,
                        onValueChange = { blocks = it },
                        label = { Text("Size in 4KB Blocks") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = attr,
                        onValueChange = { attr = it },
                        label = { Text("Flags (RW / RO)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onAddPartition(
                            name,
                            file,
                            blocks.toLongOrNull() ?: 16384L,
                            attr
                        )
                        showAddPartitionDialog = false
                    }
                ) {
                    Text("Add Aligned Entry")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAddPartitionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSaveProfileDialog) {
        var profileName by remember { mutableStateOf("SM-S918B_DroidFlasher_Profile") }
        AlertDialog(
            onDismissRequest = { showSaveProfileDialog = false },
            title = { Text("Save PIT Profile to DroidFlasher Library") },
            text = {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("Profile Label") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSavePitProfile(profileName)
                        showSaveProfileDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSaveProfileDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
