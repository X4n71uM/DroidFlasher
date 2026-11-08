package net.xantium.droidflasher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.xantium.droidflasher.model.GuideTopic
import net.xantium.droidflasher.model.LogLevel
import net.xantium.droidflasher.model.TerminalLogEntry
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
fun WorkshopTopStatusBar(
    selectedDevice: UsbTargetDevice?,
    onOpenGuideDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = BenchSurface,
        tonalElevation = 4.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .border(1.dp, CyanTelemetry.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = "USB OTG Status",
                            tint = CyanTelemetry,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "DROIDFLASHER",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val statusColor = when {
                                selectedDevice == null -> HazardCrimson
                                selectedDevice.handshakeVerified -> SignalGreen
                                else -> AmberCaution
                            }
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                        }
                        Text(
                            text = selectedDevice?.let {
                                "${it.modelCode} • ${it.protocol.shortCode} (${it.formattedVidPid})"
                            } ?: "No OTG Target Attached",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onOpenGuideDrawer,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("open_guide_drawer_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "Open Step-by-Step Workshop Guide",
                        tint = CyanTelemetry
                    )
                }
            }

            HorizontalDivider(color = BenchCardBorder)
        }
    }
}

@Composable
fun ContextualTooltipBanner(
    tooltipText: String?,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(visible = !tooltipText.isNullOrBlank()) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, CyanTelemetry.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Technical Tooltip",
                    tint = CyanTelemetry,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = tooltipText.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss Tooltip",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LiveTerminalLogDock(
    logs: List<TerminalLogEntry>,
    activeFilter: LogLevel?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelectFilter: (LogLevel?) -> Unit,
    onClearLogs: () -> Unit,
    onExportLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredLogs = if (activeFilter == null) logs else logs.filter { it.level == activeFilter }
    val listState = rememberLazyListState()

    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Surface(
        color = Color(0xFF070A0F),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, BenchCardBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BenchSurface)
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("terminal_toggle_header"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Terminal Console",
                        tint = CyanTelemetry,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE USB OTG & PACKET LOG (${filteredLogs.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = CyanTelemetry
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onExportLogs,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("export_terminal_log_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Export Session Log",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onClearLogs,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("clear_terminal_log_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Terminal Log",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                        contentDescription = if (isExpanded) "Collapse Terminal" else "Expand Terminal",
                        tint = CyanTelemetry
                    )
                }
            }

            if (isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = activeFilter == null,
                        onClick = { onSelectFilter(null) },
                        label = { Text("ALL", style = MaterialTheme.typography.labelSmall) }
                    )
                    LogLevel.entries.forEach { level ->
                        FilterChip(
                            selected = activeFilter == level,
                            onClick = { onSelectFilter(if (activeFilter == level) null else level) },
                            label = { Text(level.badge, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            val logBoxHeight = if (isExpanded) 190.dp else 58.dp
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(logBoxHeight)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                items(filteredLogs, key = { it.id }) { item ->
                    val badgeColor = when (item.level) {
                        LogLevel.INFO -> CyanTelemetry
                        LogLevel.PACKET -> Color(0xFFB388FF)
                        LogLevel.SUCCESS -> SignalGreen
                        LogLevel.WARN -> AmberCaution
                        LogLevel.ERROR -> HazardCrimson
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "${item.timestamp} ",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = TextSecondary
                        )
                        Text(
                            text = "[${item.tag}] ",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.message,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isExpanded && item.hexPayload.isNotBlank()) {
                                Text(
                                    text = "HEX: ${item.hexPayload}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = CyanTelemetry.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WorkshopHelperDrawerContent(
    topics: List<GuideTopic>,
    onCloseDrawer: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(330.dp)
            .background(BenchSurface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = CyanTelemetry
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DROIDFLASHER SOP GUIDE",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onCloseDrawer) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Guide Drawer",
                    tint = TextSecondary
                )
            }
        }

        Text(
            text = "Step-by-step technician operating procedures for USB OTG flashing, ADB file push, EFS protection, and PIT binary engineering.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        topics.forEach { topic ->
            Card(
                colors = CardDefaults.cardColors(containerColor = BenchSurfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, BenchCardBorder, RoundedCornerShape(12.dp))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = topic.badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = CyanTelemetry
                    )
                    Text(
                        text = topic.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = topic.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    topic.steps.forEachIndexed { idx, step ->
                        Text(
                            text = "${idx + 1}. $step",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(
                        color = Color(0xFF1F1600),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "NOTE: ${topic.technicalNote}",
                            style = MaterialTheme.typography.labelSmall,
                            color = AmberCaution,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RepartitionHazardModal(
    hasRecentEfsBackup: Boolean,
    onBackupEfsNow: () -> Unit,
    onConfirmRepartition: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Destructive Operation Warning",
                tint = HazardCrimson,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "DANGER: FULL NAND REPARTITIONING",
                style = MaterialTheme.typography.titleLarge,
                color = HazardCrimson
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Writing a modified PIT table rewrites the LBA partition boundaries on the target UFS/eMMC chip. Any partition whose start offset or block count changes will be formatted.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Surface(
                    color = if (hasRecentEfsBackup) Color(0xFF06331D) else Color(0xFF3B0A12),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = if (hasRecentEfsBackup) SignalGreen else HazardCrimson
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasRecentEfsBackup) {
                                "EFS / Radio Backup Verified in current session."
                            } else {
                                "NO EFS/NVRAM BACKUP DETECTED! Back up EFS before repartitioning to prevent permanent IMEI loss."
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (hasRecentEfsBackup) SignalGreen else Color(0xFFFFB3BC)
                        )
                    }
                }
                if (!hasRecentEfsBackup) {
                    OutlinedButton(
                        onClick = onBackupEfsNow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("modal_backup_efs_now_button")
                    ) {
                        Text("One-Tap Backup EFS & Radio Now")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmRepartition,
                colors = ButtonDefaults.buttonColors(containerColor = HazardCrimson),
                modifier = Modifier.testTag("confirm_repartition_button")
            ) {
                Text("Arm Full Repartition", color = Color.White)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.testTag("cancel_repartition_button")
            ) {
                Text("Cancel (Safe Mode)")
            }
        }
    )
}
