package com.shakeit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.shakeit.hardware.DetectionStatus
import com.shakeit.state.DefaultShakeItSnapshot
import com.shakeit.state.ShakeItState
import com.shakeit.ui.home.HomeScreen
import com.shakeit.ui.settings.SettingsScreen
import com.shakeit.ui.theme.ShakeItTheme

/**
 * Previews are rendered at the prototype's device frame (390x844) so the layout
 * can be compared side by side with `debug/mockup/shakeit-prototype.html`.
 *
 * They build state directly instead of going through [rememberShakeItState],
 * which would touch SharedPreferences.
 */
private const val PREVIEW_WIDTH_DP = 390
private const val PREVIEW_HEIGHT_DP = 844

@Composable
private fun PreviewShell(darkTheme: Boolean, content: @Composable () -> Unit) {
    ShakeItTheme(darkTheme = darkTheme, dynamicColor = false) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            content = { content() },
        )
    }
}

@Preview(name = "Home · off · light", widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun HomeOffLightPreview() {
    PreviewShell(darkTheme = false) {
        HomeScreenPreview(torchOn = false)
    }
}

@Preview(name = "Home · on · light", widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun HomeOnLightPreview() {
    PreviewShell(darkTheme = false) {
        HomeScreenPreview(torchOn = true)
    }
}

@Preview(name = "Home · off · dark", widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun HomeOffDarkPreview() {
    PreviewShell(darkTheme = true) {
        HomeScreenPreview(torchOn = false)
    }
}

@Preview(name = "Home · on · dark", widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun HomeOnDarkPreview() {
    PreviewShell(darkTheme = true) {
        HomeScreenPreview(torchOn = true)
    }
}

@Preview(name = "Settings · light", widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun SettingsLightPreview() {
    PreviewShell(darkTheme = false) {
        SettingsScreenPreview()
    }
}

@Preview(name = "Settings · dark", widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun SettingsDarkPreview() {
    PreviewShell(darkTheme = true) {
        SettingsScreenPreview()
    }
}

@Composable
private fun HomeScreenPreview(torchOn: Boolean) {
    HomeScreen(
        torchOn = torchOn,
        activations = DefaultShakeItSnapshot.activations,
        detectionStatus = DetectionStatus.ACTIVE,
        detectedShake = 0,
        // Frozen in previews: the frame loop would otherwise keep the preview
        // redrawing indefinitely.
        animateBlob = false,
        onToggleTorch = {},
        onOpenSettings = {},
        onToggleTheme = {},
    )
}

@Composable
private fun SettingsScreenPreview() {
    val state = remember { ShakeItState(DefaultShakeItSnapshot) }
    SettingsScreen(state = state, onBack = {})
}
