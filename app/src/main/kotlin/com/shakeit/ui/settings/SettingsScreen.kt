package com.shakeit.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.BatterySaver
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.shakeit.ui.components.OutlineButton
import com.shakeit.ui.components.SegmentedControl
import com.shakeit.ui.components.ShakeItScreen
import com.shakeit.ui.components.ShakeItSlider
import com.shakeit.ui.components.ShakeItSwitch
import com.shakeit.ui.theme.ShakeItTheme
import com.shakeit.ui.theme.ShakeItType

/**
 * The settings screen, and the diagnostics page that lives at the bottom of it.
 *
 * Everything reported here comes from a [DiagnosticsSnapshot] the engine re-reads
 * whenever the app is resumed, so the answers are facts about this device right
 * now rather than stored preferences — including the ones that say "the platform
 * will not tell me".
 *
 * @param diagnostics the current facts. Defaults to an empty snapshot so previews
 *   and the first composition have something to render.
 * @param onOpenBatterySettings opens the standard battery-optimisation screen;
 *   only offered while the Doze answer is not "exempt", so the user is never
 *   pushed into system settings they do not need.
 * @param onOpenAppSettings opens this app's own system page, which is where every
 *   vendor hides its auto-start and freezer menus.
 * @param onOpenShizuku launches the Shizuku app, which is the only way to start
 *   its service or undo a denial.
 * @param onRequestShizukuPermission shows Shizuku's own grant dialog.
 * @param onRepairDozeAllowlist asks Shizuku to put *this package* on the Doze
 *   allowlist, then re-reads to confirm.
 * @param onRepairBackgroundAppOp asks Shizuku to clear *this package's*
 *   background-restriction app-op, then re-reads to confirm.
 */
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
    val colors = ShakeItTheme.colors
    ShakeItScreen(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            // Navigation Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = colors.onSurface,
                    )
                }
            }
            // Prominent Title Banner (Matching Screenshot Reference)
            Text(
                text = stringResource(R.string.settings_title),
                style = ShakeItType.settingsTitle.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.onSurface,
                modifier = Modifier.padding(start = 8.dp, bottom = 18.dp),
            )
            // Grouped Settings Cards
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Shake Mechanics
                SettingsCardGroup {
                    SettingsControlRow(
                        icon = Icons.Rounded.Tune,
                        title = stringResource(R.string.setting_sensitivity),
                        subtitle = state.sensitivityLabel,
                    ) {
                        ShakeItSlider(
                            value = state.sensitivity,
                            onValueChange = state::setSensitivity,
                            modifier = Modifier.padding(top = 4.dp),
                            valueRange = Sensitivity.MIN..Sensitivity.MAX,
                            valueLabel = state.sensitivityLabel,
                            description = stringResource(R.string.setting_sensitivity),
                        )
                    }
                    SettingsDivider()
                    SettingsControlRow(
                        icon = Icons.Rounded.Gesture,
                        title = "Gesture",
                        subtitle = state.gesture.label,
                    ) {
                        Column {
                            SegmentedControl(
                                options = ShakeGesture.values().toList(),
                                selected = state.gesture,
                                labelOf = { it.label },
                                onSelect = { state.gesture = it },
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            // Said out loud rather than left implied: the
                            // recognition layer implements Shake, and a control
                            // that looks like it changes behaviour without
                            // changing it is the same lie as a notification that
                            // claims to be listening.
                            if (state.gesture != ShakeGesture.Shake) {
                                Text(
                                    text = stringResource(R.string.gesture_not_implemented),
                                    style = ShakeItType.finePrint,
                                    color = colors.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                    }
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Vibration,
                        title = stringResource(R.string.setting_detection_active),
                        subtitle = if (state.detectionActive) "Active" else "Paused",
                        checked = state.detectionActive,
                        onCheckedChange = { state.detectionActive = it },
                    )
                }
                // Flashlight Reliability
                SettingsCardGroup {
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Timer,
                        title = stringResource(R.string.setting_auto_off),
                        subtitle = "Automatically turn off after 5 minutes",
                        checked = state.autoOffAfterFiveMinutes,
                        onCheckedChange = { state.autoOffAfterFiveMinutes = it },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Rounded.RestartAlt,
                        title = stringResource(R.string.setting_start_after_reboot),
                        subtitle = "Resume shake detection on device boot",
                        checked = state.startAfterReboot,
                        onCheckedChange = { state.startAfterReboot = it },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Rounded.PowerSettingsNew,
                        title = stringResource(R.string.setting_run_in_background),
                        subtitle = "Keep background service active",
                        checked = state.runInBackground,
                        onCheckedChange = { state.runInBackground = it },
                    )
                }
                // Appearance Section
                SettingsCardGroup {
                    SettingsControlRow(
                        icon = Icons.Rounded.Palette,
                        title = "Theme",
                        subtitle = state.themeMode.label,
                    ) {
                        SegmentedControl(
                            options = ThemeMode.values().toList(),
                            selected = state.themeMode,
                            labelOf = { it.label },
                            onSelect = { state.themeMode = it },
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = Icons.Rounded.Wallpaper,
                        title = stringResource(R.string.setting_dynamic_color),
                        subtitle = "Adapt colors to device wallpaper",
                        checked = state.dynamicColor,
                        onCheckedChange = { state.dynamicColor = it },
                    )
                }
                // Advanced Section
                SettingsCardGroup {
                    val power = diagnostics.power
                    val vendor = power?.vendor ?: PowerManagerVendor.Stock
                    // One mechanism, named as itself: the Android/Doze
                    // power-exemption allowlist. It used to be labelled
                    // "Unrestricted", which read as a claim about every vendor
                    // switch too — the other mechanisms are reported separately,
                    // each with its own answer, in the diagnostics card below.
                    val dozeExempt = power?.dozeExemption == RestrictionState.ALLOWED
                    SettingsControlRow(
                        icon = if (dozeExempt) Icons.Rounded.BatteryFull
                        else Icons.Rounded.BatteryAlert,
                        title = stringResource(R.string.setting_background_reliability),
                        subtitle = stringResource(
                            if (dozeExempt) R.string.background_unrestricted
                            else R.string.background_restricted,
                        ),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (!dozeExempt) {
                                OutlineButton(
                                    text = stringResource(R.string.background_open_settings),
                                    onClick = onOpenBatterySettings,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            // ShakeIT's own app-info page: the doorway every
                            // vendor uses for its battery, auto-start and
                            // freezer menus, and the one screen an AOSP intent
                            // can reliably open.
                            OutlineButton(
                                text = stringResource(R.string.background_open_app_settings),
                                onClick = onOpenAppSettings,
                                modifier = Modifier.padding(top = if (dozeExempt) 8.dp else 0.dp),
                            )
                            // Named steps for this device's own power manager.
                            // Shown even when Android's own answer is fine,
                            // because a vendor freezer or auto-start manager is a
                            // different mechanism that this app cannot read.
                            if (vendor != PowerManagerVendor.Stock || !dozeExempt) {
                                Text(
                                    text = stringResource(
                                        when (vendor) {
                                            PowerManagerVendor.Transsion ->
                                                R.string.background_vendor_transsion
                                            PowerManagerVendor.Xiaomi ->
                                                R.string.background_vendor_xiaomi
                                            PowerManagerVendor.Stock ->
                                                R.string.background_vendor_stock
                                        },
                                    ),
                                    style = ShakeItType.finePrint,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    SettingsDivider()
                    SettingsControlRow(
                        icon = Icons.Rounded.Build,
                        title = stringResource(R.string.setting_shizuku),
                        subtitle = stringResource(shizukuSubtitle(diagnostics.shizuku.state)),
                    ) {
                        ShizukuControls(
                            status = diagnostics.shizuku,
                            lastAction = diagnostics.lastPrivilegedAction,
                            onOpenShizuku = onOpenShizuku,
                            onRequestPermission = onRequestShizukuPermission,
                            onRepairDozeAllowlist = onRepairDozeAllowlist,
                            onRepairBackgroundAppOp = onRepairBackgroundAppOp,
                        )
                    }
                    SettingsDivider()
                    Text(
                        text = stringResource(R.string.advanced_fine_print),
                        style = ShakeItType.finePrint,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                // Diagnostics: the facts the app can prove about itself, on one
                // page that is still there after the app is reopened — which is
                // exactly when "it stopped working overnight" has to be answered.
                // Nothing here is inferred from anything else, and a mechanism
                // that will not answer says so instead of defaulting to "fine".
                SettingsCardGroup {
                    DiagnosticsRows(diagnostics)
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

/**
 * What Shizuku's own state means for this app, in the words the row shows.
 *
 * Five states rather than connected/not, because "installed but not running",
 * "running but not granted" and "denied" all need a different action from the
 * user, and only the last one is out of this app's hands.
 */
@StringRes
private fun shizukuSubtitle(state: ShizukuState): Int = when (state) {
    ShizukuState.NOT_INSTALLED -> R.string.shizuku_not_installed
    ShizukuState.NOT_RUNNING -> R.string.shizuku_not_running
    ShizukuState.PERMISSION_NEEDED -> R.string.shizuku_permission_needed
    ShizukuState.DENIED -> R.string.shizuku_denied
    ShizukuState.READY -> R.string.shizuku_ready
}

/**
 * Shizuku's controls, offered only for the states where they can do something.
 *
 * The two repairs are the only privileged writes in the app. Both are explicit
 * (a button, never automatic), both touch this package alone, and both are
 * followed by a re-read so the reported result is what the platform now says
 * rather than what the tap hoped for — that result is [lastAction].
 */
@Composable
private fun ShizukuControls(
    status: ShizukuStatus,
    lastAction: PrivilegedAction?,
    onOpenShizuku: () -> Unit,
    onRequestPermission: () -> Unit,
    onRepairDozeAllowlist: () -> Unit,
    onRepairBackgroundAppOp: () -> Unit,
) {
    val colors = ShakeItTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (status.state) {
            // Nothing to open and nothing to ask for: the row's own text says it
            // is optional, and no button pretends otherwise.
            ShizukuState.NOT_INSTALLED -> Unit
            ShizukuState.NOT_RUNNING, ShizukuState.DENIED -> OutlineButton(
                text = stringResource(R.string.shizuku_open),
                onClick = onOpenShizuku,
                modifier = Modifier.padding(top = 8.dp),
            )
            ShizukuState.PERMISSION_NEEDED -> OutlineButton(
                text = stringResource(R.string.shizuku_grant),
                onClick = onRequestPermission,
                modifier = Modifier.padding(top = 8.dp),
            )
            ShizukuState.READY -> {
                OutlineButton(
                    text = stringResource(R.string.shizuku_repair_doze),
                    onClick = onRepairDozeAllowlist,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlineButton(
                    text = stringResource(R.string.shizuku_repair_appop),
                    onClick = onRepairBackgroundAppOp,
                )
            }
        }
        if (lastAction != null) {
            Text(
                text = (if (lastAction.succeeded) "Changed — " else "Not changed — ") +
                    lastAction.description,
                style = ShakeItType.finePrint,
                color = if (lastAction.succeeded) colors.accent else colors.primary,
            )
        }
        if (status.state != ShizukuState.NOT_INSTALLED) {
            Text(
                text = stringResource(R.string.shizuku_not_root),
                style = ShakeItType.finePrint,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

/** One line of the diagnostics report: an optional icon, a label and a fact. */
private class DiagnosticsRow(
    val icon: ImageVector?,
    val title: String,
    val value: String,
)

/**
 * The diagnostics report.
 *
 * Rows with an icon are the headline answers; the rest continue underneath them,
 * indented to the same text column, so the card reads as three short reports
 * (service and detector, sensors and wake lock, power policy and history) rather
 * than seventeen unrelated settings.
 */
@Composable
private fun DiagnosticsRows(diagnostics: DiagnosticsSnapshot) {
    val sensor: SensorDiagnostics = diagnostics.sensor
    val power = diagnostics.power
    // Keyed on the snapshot so an age is recomputed when new facts arrive, and
    // not on every recomposition, which would make the page flicker.
    val nowMillis = remember(diagnostics) { System.currentTimeMillis() }

    val rows = listOf(
        DiagnosticsRow(
            icon = Icons.Rounded.Notifications,
            title = "Service",
            value = DiagnosticsText.service(
                running = diagnostics.serviceRunning,
                wanted = diagnostics.wantsBackgroundService,
            ),
        ),
        DiagnosticsRow(
            icon = Icons.Rounded.Sensors,
            title = "Detector",
            value = DiagnosticsText.detector(
                status = diagnostics.detectionStatus,
                attempts = diagnostics.recoveryAttempts,
                maxAttempts = diagnostics.maxRecoveryAttempts,
            ),
        ),
        DiagnosticsRow(
            icon = Icons.Rounded.Schedule,
            title = "Last accelerometer sample",
            value = DiagnosticsText.lastSample(sensor),
        ),
        DiagnosticsRow(
            icon = null,
            title = "Accelerometer",
            value = DiagnosticsText.accelerometer(sensor),
        ),
        DiagnosticsRow(
            icon = null,
            title = "Listener registration",
            value = DiagnosticsText.registration(sensor),
        ),
        DiagnosticsRow(
            icon = null,
            title = "Wake lock",
            value = DiagnosticsText.wakeLock(sensor),
        ),
        DiagnosticsRow(
            icon = null,
            title = "Proximity (pocket guard)",
            value = DiagnosticsText.proximity(sensor),
        ),
        DiagnosticsRow(
            icon = null,
            title = "Significant motion trigger",
            value = DiagnosticsText.significantMotion(sensor),
        ),
        DiagnosticsRow(
            icon = Icons.Rounded.BatterySaver,
            title = "Doze exemption",
            value = DiagnosticsText.dozeExemption(power?.dozeExemption),
        ),
        DiagnosticsRow(
            icon = null,
            title = "Background activity (app-op)",
            value = DiagnosticsText.backgroundAppOps(power?.backgroundAppOps),
        ),
        DiagnosticsRow(
            icon = null,
            title = "Vendor auto-start",
            value = DiagnosticsText.autoStart(power?.autoStart),
        ),
        DiagnosticsRow(
            icon = null,
            title = "Low Power Standby",
            value = DiagnosticsText.lowPowerStandby(power?.lowPowerStandby),
        ),
        DiagnosticsRow(
            icon = null,
            title = "Power mode",
            value = DiagnosticsText.powerModeRightNow(
                deviceIdle = power?.deviceIdleMode ?: false,
                powerSave = power?.powerSaveMode ?: false,
            ),
        ),
        DiagnosticsRow(
            icon = Icons.Rounded.BugReport,
            title = "Previous process exit",
            value = DiagnosticsText.previousExit(
                exit = diagnostics.previousExit,
                supported = diagnostics.exitHistorySupported,
                nowMillis = nowMillis,
            ),
        ),
        DiagnosticsRow(
            icon = null,
            title = "This process",
            value = DiagnosticsText.processUptime(diagnostics.processUptimeMillis),
        ),
        DiagnosticsRow(
            icon = null,
            title = "Shizuku",
            value = DiagnosticsText.shizuku(diagnostics.shizuku),
        ),
        DiagnosticsRow(
            icon = Icons.Rounded.PhoneAndroid,
            title = "Device",
            value = power?.let {
                DiagnosticsText.device(it.manufacturer, it.model, it.sdkInt)
            } ?: "Not read yet",
        ),
        DiagnosticsRow(
            icon = Icons.Rounded.Speed,
            title = "Preferences",
            value = "Detection ${if (diagnostics.wantsDetection) "on" else "off"} · " +
                "background ${if (diagnostics.wantsBackgroundService) "on" else "off"} · " +
                "reboot ${if (diagnostics.wantsStartAfterReboot) "on" else "off"} · " +
                "auto-off ${if (diagnostics.wantsAutoOff) "on" else "off"} · " +
                "sensitivity ${diagnostics.sensitivity}",
        ),
    )

    rows.forEachIndexed { index, row ->
        if (index > 0) SettingsDivider()
        SettingsInfoRow(icon = row.icon, title = row.title, value = row.value)
    }
}

/**
 * A read-only row: a label and the fact beside it.
 *
 * Rows without an icon are continuations of the row above, so they are indented
 * to the same text column (16dp padding + 24dp icon + 16dp gap) instead of
 * starting at the card edge.
 */
@Composable
private fun SettingsInfoRow(
    icon: ImageVector?,
    title: String,
    value: String,
) {
    val colors = ShakeItTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = if (icon == null) 56.dp else 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 12.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = ShakeItType.rowLabel.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.onSurface,
            )
            Text(
                text = value,
                style = ShakeItType.caption,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCardGroup(
    content: @Composable () -> Unit,
) {
    val colors = ShakeItTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface2)
            .padding(vertical = 4.dp),
    ) {
        content()
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = ShakeItTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = ShakeItType.rowLabel.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = colors.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = ShakeItType.caption,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        ShakeItSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingsControlRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    val colors = ShakeItTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = ShakeItType.rowLabel.copy(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = ShakeItType.caption,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsDivider() {
    val colors = ShakeItTheme.colors
    HorizontalDivider(
        thickness = 0.5.dp,
        color = colors.outline.copy(alpha = 0.25f),
    )
}
