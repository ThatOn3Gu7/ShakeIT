package com.shakeit.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shakeit.R
import com.shakeit.state.Sensitivity
import com.shakeit.state.ShakeGesture
import com.shakeit.state.ShakeItState
import com.shakeit.state.ThemeMode
import com.shakeit.ui.components.IconActionButton
import com.shakeit.ui.components.OutlineButton
import com.shakeit.ui.components.SegmentedControl
import com.shakeit.ui.components.ShakeItScreen
import com.shakeit.ui.components.ShakeItSlider
import com.shakeit.ui.components.ShakeItSwitch
import com.shakeit.ui.theme.ShakeItTheme
import com.shakeit.ui.theme.ShakeItType

/** `←` — the prototype's back glyph. */
private const val BACK_GLYPH = "\u2190"

private val DividerThickness = 1.dp

/**
 * The `#settings` screen: a fixed top bar over a scrolling stack of groups,
 * each separated by a hairline rule (`.group + .group { border-top: ... }`).
 *
 * Takes the whole [ShakeItState] because it binds every setting; the individual
 * rows below stay stateless.
 */
@Composable
fun SettingsScreen(
    state: ShakeItState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ShakeItTheme.colors

    ShakeItScreen(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconActionButton(
                glyph = BACK_GLYPH,
                description = stringResource(R.string.cd_back),
                onClick = onBack,
            )
            Text(
                text = stringResource(R.string.settings_title),
                style = ShakeItType.settingsTitle,
                color = colors.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            // `#settings` balances the back button with an empty 38px div.
            Spacer(Modifier.width(38.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 6.dp),
        ) {
            SettingsGroup(title = stringResource(R.string.group_shake_mechanics), first = true) {
                LabeledValueRow(
                    label = stringResource(R.string.setting_sensitivity),
                    value = state.sensitivityLabel,
                )
                ShakeItSlider(
                    value = state.sensitivity,
                    onValueChange = state::setSensitivity,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                    valueRange = Sensitivity.MIN..Sensitivity.MAX,
                    valueLabel = state.sensitivityLabel,
                    description = stringResource(R.string.setting_sensitivity),
                )
                SegmentedControl(
                    options = ShakeGesture.values().toList(),
                    selected = state.gesture,
                    labelOf = { it.label },
                    onSelect = { state.gesture = it },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                SwitchRow(
                    label = stringResource(R.string.setting_detection_active),
                    checked = state.detectionActive,
                    onCheckedChange = { state.detectionActive = it },
                )
            }

            SettingsGroup(title = stringResource(R.string.group_flashlight_reliability)) {
                SwitchRow(
                    label = stringResource(R.string.setting_auto_off),
                    checked = state.autoOffAfterFiveMinutes,
                    onCheckedChange = { state.autoOffAfterFiveMinutes = it },
                )
                SwitchRow(
                    label = stringResource(R.string.setting_start_after_reboot),
                    checked = state.startAfterReboot,
                    onCheckedChange = { state.startAfterReboot = it },
                )
                SwitchRow(
                    label = stringResource(R.string.setting_run_in_background),
                    checked = state.runInBackground,
                    onCheckedChange = { state.runInBackground = it },
                )
            }

            SettingsGroup(title = stringResource(R.string.group_appearance)) {
                SegmentedControl(
                    options = ThemeMode.values().toList(),
                    selected = state.themeMode,
                    labelOf = { it.label },
                    onSelect = { state.themeMode = it },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                SwitchRow(
                    label = stringResource(R.string.setting_dynamic_color),
                    checked = state.dynamicColor,
                    onCheckedChange = { state.dynamicColor = it },
                )
            }

            SettingsGroup(title = stringResource(R.string.group_advanced)) {
                // `.row-text-block`
                Column(
                    modifier = Modifier.padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = stringResource(R.string.setting_shizuku),
                        style = ShakeItType.rowLabel,
                        color = colors.onSurface,
                    )
                    Text(
                        text = stringResource(
                            if (state.shizukuConnected) R.string.shizuku_connected
                            else R.string.shizuku_disconnected,
                        ),
                        style = ShakeItType.captionBlock,
                        color = colors.onSurfaceVariant,
                    )
                }
                OutlineButton(
                    text = stringResource(
                        if (state.shizukuConnected) R.string.shizuku_connect_done
                        else R.string.shizuku_connect,
                    ),
                    onClick = state::connectShizuku,
                    enabled = !state.shizukuConnected,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Text(
                    text = stringResource(R.string.advanced_fine_print),
                    style = ShakeItType.finePrint,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * `.group` — 18px vertical / 2px horizontal padding with a 12.5px bold
 * `--primary` heading, preceded by a hairline rule for every group but the
 * first.
 */
@Composable
private fun SettingsGroup(
    title: String,
    first: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = ShakeItTheme.colors

    if (!first) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(DividerThickness)
                .background(colors.outline),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 18.dp),
    ) {
        Text(
            text = title,
            style = ShakeItType.groupHeader,
            color = colors.primary,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        content()
    }
}

/** `.row` holding a `.row-text` — a label with a value line beneath it. */
@Composable
private fun LabeledValueRow(label: String, value: String) {
    val colors = ShakeItTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(text = label, style = ShakeItType.rowLabel, color = colors.onSurface)
            Text(text = value, style = ShakeItType.caption, color = colors.onSurfaceVariant)
        }
    }
}

/** `.row.switch-row` — a 14px label opposite the toggle. */
@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = ShakeItTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = ShakeItType.rowLabel,
            color = colors.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
        )
        ShakeItSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
