package com.shakeit.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.shakeit.R
import com.shakeit.background.PowerManagerVendor
import com.shakeit.background.RestrictionState
import com.shakeit.background.ShizukuState
import com.shakeit.background.ShizukuStatus
import com.shakeit.engine.DiagnosticsSnapshot
import com.shakeit.engine.PrivilegedAction
import com.shakeit.hardware.SensorDiagnostics
import com.shakeit.state.Sensitivity
import com.shakeit.state.ShakeGesture
import com.shakeit.state.ShakeItState
import com.shakeit.state.ThemeMode
import com.shakeit.ui.components.SettingsActionButton
import com.shakeit.ui.components.SettingsControlRow
import com.shakeit.ui.components.SettingsInfoRow
import com.shakeit.ui.components.SettingsPanel
import com.shakeit.ui.components.SettingsRow
import com.shakeit.ui.components.SettingsSectionHeader
import com.shakeit.ui.components.SettingsSwitchRow
import com.shakeit.ui.components.ShakeItIconButton
import com.shakeit.ui.components.ShakeItScreen
import com.shakeit.ui.components.ShakeItSegmentedRow
import com.shakeit.ui.settings.DiagnosticsText
import com.shakeit.ui.theme.ShakeItMotion
import com.shakeit.ui.theme.ShakeItShapes

/**
 * Settings is a user-facing control surface first and an engineering report
 * second. Everyday controls get named sections and room; reliability actions are
 * grouped as advanced; diagnostics is a collapsed, read-only report that remains
 * available after reopening the app without making the first settings view look
 * like a console.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: ShakeItState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    diagnostics: DiagnosticsSnapshot = DiagnosticsSnapshot(),
    onOpenBatterySettings: () -> Unit = {},
    onOpenAppSettings: () -> Unit = {},
    onOpenShizuku: () -> Unit = {},
    onRequestShizukuPermission: () -> Unit = {},
    onRepairDozeAllowlist: () -> Unit = {},
    onRepairBackgroundAppOp: () -> Unit = {},
) {
    var diagnosticsOpen by remember { mutableStateOf(false) }

    ShakeItScreen(modifier = modifier) { metrics ->
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ShakeItIconButton(
                    icon = Icons.Rounded.ArrowBack,
                    description = stringResource(R.string.cd_back),
                    onClick = onBack,
                )
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.size(48.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = metrics.sectionGap, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(metrics.sectionGap),
            ) {
                SettingsSectionHeader(
                    title = stringResource(R.string.settings_section_everyday),
                    support = stringResource(R.string.settings_section_everyday_support),
                )
                SettingsPanel {
                    SettingsControlRow(
                        icon = Icons.Rounded.Tune,
                        title = stringResource(R.string.setting_sensitivity),
                        subtitle = state.sensitivityLabel,
                    ) {
                        SensitivitySteps(
                            value = state.sensitivity,
                            onValueChange = state::setSensitivity,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    SettingsDivider()
                    SettingsControlRow(
                        icon = Icons.Rounded.Gesture,
                        title = stringResource(R.string.setting_gesture),
                        subtitle = state.gesture.label,
                        alignToText = false,
                    ) {
                        ShakeItSegmentedRow(
                            options = ShakeGesture.values().toList(),
                            selected = state.gesture,
                            labelOf = { it.label },
                            onSelect = { state.gesture = it },
                        )
                        if (state.gesture != ShakeGesture.Shake) {
                            Text(
                                text = stringResource(R.string.gesture_not_implemented),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Sensors,
                        title = stringResource(R.string.setting_detection_active),
                        subtitle = if (state.detectionActive) {
                            stringResource(R.string.settings_detection_active_subtitle)
                        } else {
                            stringResource(R.string.settings_detection_paused_subtitle)
                        },
                        checked = state.detectionActive,
                        onCheckedChange = { state.detectionActive = it },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Rounded.FlashlightOn,
                        title = stringResource(R.string.setting_auto_off),
                        subtitle = stringResource(R.string.settings_auto_off_subtitle),
                        checked = state.autoOffAfterFiveMinutes,
                        onCheckedChange = { state.autoOffAfterFiveMinutes = it },
                    )
                }

                SettingsSectionHeader(
                    title = stringResource(R.string.settings_section_appearance),
                    support = stringResource(R.string.settings_section_appearance_support),
                )
                SettingsPanel(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    SettingsControlRow(
                        icon = Icons.Rounded.Palette,
                        title = stringResource(R.string.setting_theme),
                        subtitle = state.themeMode.label,
                        alignToText = false,
                    ) {
                        ShakeItSegmentedRow(
                            options = ThemeMode.values().toList(),
                            selected = state.themeMode,
                            labelOf = { it.label },
                            onSelect = { state.themeMode = it },
                        )
                    }
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Wallpaper,
                        title = stringResource(R.string.setting_dynamic_color),
                        subtitle = stringResource(R.string.settings_dynamic_color_subtitle),
                        checked = state.dynamicColor,
                        onCheckedChange = { state.dynamicColor = it },
                    )
                }

                SettingsSectionHeader(
                    title = stringResource(R.string.settings_section_reliability),
                    support = stringResource(R.string.settings_section_reliability_support),
                )
                SettingsPanel {
                    SettingsSwitchRow(
                        icon = Icons.Rounded.PowerSettingsNew,
                        title = stringResource(R.string.setting_run_in_background),
                        subtitle = stringResource(R.string.settings_run_background_subtitle),
                        checked = state.runInBackground,
                        onCheckedChange = { state.runInBackground = it },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Rounded.RestartAlt,
                        title = stringResource(R.string.setting_start_after_reboot),
                        subtitle = stringResource(R.string.settings_reboot_subtitle),
                        checked = state.startAfterReboot,
                        onCheckedChange = { state.startAfterReboot = it },
                    )
                    SettingsDivider()
                    BackgroundReliability(
                        diagnostics = diagnostics,
                        onOpenBatterySettings = onOpenBatterySettings,
                        onOpenAppSettings = onOpenAppSettings,
                    )
                    SettingsDivider()
                    ShizukuSection(
                        status = diagnostics.shizuku,
                        lastAction = diagnostics.lastPrivilegedAction,
                        onOpenShizuku = onOpenShizuku,
                        onRequestPermission = onRequestShizukuPermission,
                        onRepairDozeAllowlist = onRepairDozeAllowlist,
                        onRepairBackgroundAppOp = onRepairBackgroundAppOp,
                    )
                }

                SettingsSectionHeader(
                    title = stringResource(R.string.settings_section_diagnostics),
                    support = stringResource(R.string.settings_section_diagnostics_support),
                )
                DiagnosticsPanel(
                    diagnostics = diagnostics,
                    expanded = diagnosticsOpen,
                    onToggle = { diagnosticsOpen = !diagnosticsOpen },
                )
            }
        }
    }
}

@Composable
private fun SensitivitySteps(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .height(48.dp)
            .semantics {
                stateDescription = Sensitivity.label(value)
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        (Sensitivity.MIN..Sensitivity.MAX).forEach { level ->
            val selected = level <= value
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(if (level == value) 28.dp else 18.dp)
                    .background(
                        color = if (selected) colors.primary else colors.outlineVariant,
                        shape = RoundedCornerShape(50),
                    )
                    .clickable(
                        onClickLabel = Sensitivity.label(level),
                        role = Role.Button,
                        onClick = { onValueChange(level) },
                    ),
            )
        }
    }
}

@Composable
private fun BackgroundReliability(
    diagnostics: DiagnosticsSnapshot,
    onOpenBatterySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val power = diagnostics.power
    val vendor = power?.vendor ?: PowerManagerVendor.Stock
    val exempt = power?.dozeExemption == RestrictionState.ALLOWED
    SettingsControlRow(
        icon = if (exempt) Icons.Rounded.BatteryFull else Icons.Rounded.BatteryAlert,
        title = stringResource(R.string.setting_background_reliability),
        subtitle = if (exempt) {
            stringResource(R.string.background_unrestricted)
        } else {
            stringResource(R.string.background_restricted)
        },
        alignToText = false,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!exempt) {
                SettingsActionButton(
                    text = stringResource(R.string.background_open_settings),
                    icon = Icons.Rounded.BatterySaver,
                    onClick = onOpenBatterySettings,
                    prominent = true,
                )
            }
            SettingsActionButton(
                text = stringResource(R.string.background_open_app_settings),
                icon = Icons.Rounded.PowerSettingsNew,
                onClick = onOpenAppSettings,
            )
            if (vendor != PowerManagerVendor.Stock || !exempt) {
                Text(
                    text = stringResource(
                        when (vendor) {
                            PowerManagerVendor.Transsion -> R.string.background_vendor_transsion
                            PowerManagerVendor.Xiaomi -> R.string.background_vendor_xiaomi
                            PowerManagerVendor.Stock -> R.string.background_vendor_stock
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ShizukuSection(
    status: ShizukuStatus,
    lastAction: PrivilegedAction?,
    onOpenShizuku: () -> Unit,
    onRequestPermission: () -> Unit,
    onRepairDozeAllowlist: () -> Unit,
    onRepairBackgroundAppOp: () -> Unit,
) {
    SettingsControlRow(
        icon = Icons.Rounded.Build,
        title = stringResource(R.string.setting_shizuku),
        subtitle = stringResource(shizukuSubtitle(status.state)),
        alignToText = false,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (status.state) {
                ShizukuState.NOT_INSTALLED -> Unit
                ShizukuState.NOT_RUNNING, ShizukuState.DENIED -> SettingsActionButton(
                    text = stringResource(R.string.shizuku_open),
                    onClick = onOpenShizuku,
                )
                ShizukuState.PERMISSION_NEEDED -> SettingsActionButton(
                    text = stringResource(R.string.shizuku_grant),
                    onClick = onRequestPermission,
                    prominent = true,
                )
                ShizukuState.READY -> {
                    SettingsActionButton(
                        text = stringResource(R.string.shizuku_repair_doze),
                        onClick = onRepairDozeAllowlist,
                        prominent = true,
                    )
                    SettingsActionButton(
                        text = stringResource(R.string.shizuku_repair_appop),
                        onClick = onRepairBackgroundAppOp,
                    )
                }
            }
            if (lastAction != null) {
                Text(
                    text = (if (lastAction.succeeded) "Changed — " else "Not changed — ") + lastAction.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lastAction.succeeded) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            if (status.state != ShizukuState.NOT_INSTALLED) {
                Text(
                    text = stringResource(R.string.shizuku_not_root),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsPanel(
    diagnostics: DiagnosticsSnapshot,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = ShakeItMotion.Snap,
        label = "diagnosticsChevron",
    )
    val sensor = diagnostics.sensor
    val summary = DiagnosticsText.detector(
        diagnostics.detectionStatus,
        diagnostics.recoveryAttempts,
        diagnostics.maxRecoveryAttempts,
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring()),
        shape = ShakeItShapes.panel,
        color = colors.surfaceContainerHighest,
    ) {
        Column {
            SettingsRow(
                icon = Icons.Rounded.BugReport,
                title = stringResource(R.string.setting_diagnostics),
                subtitle = summary,
                iconContainer = colors.primaryContainer,
                iconContent = colors.onPrimaryContainer,
                trailing = {
                    IconButton(onClick = onToggle) {
                        Icon(
                            imageVector = Icons.Rounded.ExpandMore,
                            contentDescription = stringResource(
                                if (expanded) R.string.cd_collapse_diagnostics
                                else R.string.cd_expand_diagnostics,
                            ),
                            modifier = Modifier.graphicsRotation(rotation),
                        )
                    }
                },
            )
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    SettingsDivider()
                    DiagnosticsRows(diagnostics)
                }
            }
        }
    }
}

private fun Modifier.graphicsRotation(rotation: Float): Modifier =
    graphicsLayer { rotationZ = rotation }

@Composable
private fun DiagnosticsRows(diagnostics: DiagnosticsSnapshot) {
    val sensor: SensorDiagnostics = diagnostics.sensor
    val power = diagnostics.power
    val nowMillis = remember(diagnostics) { System.currentTimeMillis() }
    val rows = listOf(
        DiagnosticRow(Icons.Rounded.PowerSettingsNew, "Service", DiagnosticsText.service(diagnostics.serviceRunning, diagnostics.wantsBackgroundService)),
        DiagnosticRow(Icons.Rounded.Sensors, "Detector", DiagnosticsText.detector(diagnostics.detectionStatus, diagnostics.recoveryAttempts, diagnostics.maxRecoveryAttempts)),
        DiagnosticRow(Icons.Rounded.Memory, "Last accelerometer sample", DiagnosticsText.lastSample(sensor)),
        DiagnosticRow(null, "Accelerometer", DiagnosticsText.accelerometer(sensor)),
        DiagnosticRow(null, "Listener registration", DiagnosticsText.registration(sensor)),
        DiagnosticRow(null, "Wake lock", DiagnosticsText.wakeLock(sensor)),
        DiagnosticRow(null, "Proximity (pocket guard)", DiagnosticsText.proximity(sensor)),
        DiagnosticRow(null, "Significant motion trigger", DiagnosticsText.significantMotion(sensor)),
        DiagnosticRow(Icons.Rounded.BatterySaver, "Doze exemption", DiagnosticsText.dozeExemption(power?.dozeExemption)),
        DiagnosticRow(null, "Background activity (app-op)", DiagnosticsText.backgroundAppOps(power?.backgroundAppOps)),
        DiagnosticRow(null, "Vendor auto-start", DiagnosticsText.autoStart(power?.autoStart)),
        DiagnosticRow(null, "Low Power Standby", DiagnosticsText.lowPowerStandby(power?.lowPowerStandby)),
        DiagnosticRow(null, "Power mode", DiagnosticsText.powerModeRightNow(power?.deviceIdleMode ?: false, power?.powerSaveMode ?: false)),
        DiagnosticRow(Icons.Rounded.BugReport, "Previous process exit", DiagnosticsText.previousExit(diagnostics.previousExit, diagnostics.exitHistorySupported, nowMillis)),
        DiagnosticRow(null, "This process", DiagnosticsText.processUptime(diagnostics.processUptimeMillis)),
        DiagnosticRow(null, "Shizuku", DiagnosticsText.shizuku(diagnostics.shizuku)),
        DiagnosticRow(Icons.Rounded.SettingsBackupRestore, "Device", power?.let { DiagnosticsText.device(it.manufacturer, it.model, it.sdkInt) } ?: "Not read yet"),
    )
    Column {
        rows.forEachIndexed { index, row ->
            if (index > 0) SettingsDivider()
            SettingsInfoRow(
                icon = row.icon,
                title = row.title,
                value = row.value,
                emphasised = row.icon != null,
            )
        }
        Text(
            text = stringResource(R.string.advanced_fine_print),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

private data class DiagnosticRow(
    val icon: ImageVector?,
    val title: String,
    val value: String,
)

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        thickness = 1.dp,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@androidx.annotation.StringRes
private fun shizukuSubtitle(state: ShizukuState): Int = when (state) {
    ShizukuState.NOT_INSTALLED -> R.string.shizuku_not_installed
    ShizukuState.NOT_RUNNING -> R.string.shizuku_not_running
    ShizukuState.PERMISSION_NEEDED -> R.string.shizuku_permission_needed
    ShizukuState.DENIED -> R.string.shizuku_denied
    ShizukuState.READY -> R.string.shizuku_ready
}
