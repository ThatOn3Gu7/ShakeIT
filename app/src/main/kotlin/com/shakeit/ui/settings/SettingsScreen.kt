package com.shakeit.ui.settings

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
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Gesture
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shakeit.R
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

@Composable
fun SettingsScreen(
    state: ShakeItState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
                        SegmentedControl(
                            options = ShakeGesture.values().toList(),
                            selected = state.gesture,
                            labelOf = { it.label },
                            onSelect = { state.gesture = it },
                            modifier = Modifier.padding(top = 6.dp),
                        )
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
                    SettingsControlRow(
                        icon = Icons.Rounded.Build,
                        title = stringResource(R.string.setting_shizuku),
                        subtitle = stringResource(
                            if (state.shizukuConnected) R.string.shizuku_connected
                            else R.string.shizuku_disconnected,
                        ),
                    ) {
                        OutlineButton(
                            text = stringResource(
                                if (state.shizukuConnected) R.string.shizuku_connect_done
                                else R.string.shizuku_connect,
                            ),
                            onClick = state::connectShizuku,
                            enabled = !state.shizukuConnected,
                            modifier = Modifier.padding(top = 8.dp),
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
                Spacer(modifier = Modifier.height(20.dp))
            }
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
