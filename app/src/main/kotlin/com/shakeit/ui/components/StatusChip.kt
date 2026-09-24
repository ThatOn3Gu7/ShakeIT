package com.shakeit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SensorsOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shakeit.R
import com.shakeit.hardware.DetectionStatus
import com.shakeit.ui.theme.ShakeItShapes

/**
 * A short tonal label with an icon: the smallest unit of "here is a fact" in the
 * app.
 *
 * Not clickable and not focusable — it reports, it does not offer. That is why it
 * is a [Surface] with no `onClick` rather than an `AssistChip`: a chip shape with
 * a ripple on it teaches the user that something will happen when they touch it,
 * and nothing will.
 */
@Composable
fun InfoPill(
    icon: ImageVector,
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = ShakeItShapes.pill,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Whether shake detection is actually working, in the words and colours of the
 * state the engine reported.
 *
 * Every one of the five states gets its own icon as well as its own colour, so
 * the answer survives greyscale, colour-vision differences and a glance in
 * bright sunlight: a spinner is not a warning triangle is not a pause symbol.
 */
@Composable
fun DetectionStatusChip(
    status: DetectionStatus,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val appearance = when (status) {
        DetectionStatus.ACTIVE -> ChipAppearance(
            icon = Icons.Rounded.Sensors,
            container = colors.primaryContainer,
            content = colors.onPrimaryContainer,
        )

        // Amber rather than red: a rebuild is in progress, which is the app
        // working, not the app broken.
        DetectionStatus.RECOVERING -> ChipAppearance(
            icon = Icons.Rounded.Autorenew,
            container = colors.tertiaryContainer,
            content = colors.onTertiaryContainer,
        )

        DetectionStatus.STALLED -> ChipAppearance(
            icon = Icons.Rounded.ErrorOutline,
            container = colors.errorContainer,
            content = colors.onErrorContainer,
        )

        DetectionStatus.NO_SENSOR -> ChipAppearance(
            icon = Icons.Rounded.SensorsOff,
            container = colors.surfaceContainerHighest,
            content = colors.onSurfaceVariant,
        )

        DetectionStatus.INACTIVE -> ChipAppearance(
            icon = Icons.Rounded.PauseCircle,
            container = colors.surfaceContainerHighest,
            content = colors.onSurfaceVariant,
        )
    }

    InfoPill(
        icon = appearance.icon,
        text = stringResource(detectionStatusLabel(status)),
        containerColor = appearance.container,
        contentColor = appearance.content,
        modifier = modifier,
    )
}

@Immutable
private data class ChipAppearance(
    val icon: ImageVector,
    val container: Color,
    val content: Color,
)

/**
 * The words for a detection state.
 *
 * These are the short forms, for the home screen's chip. The diagnostics page has
 * its own longer sentences, produced by `DiagnosticsText`, and the notification
 * has a third set: three different amounts of room, one set of facts.
 */
private fun detectionStatusLabel(status: DetectionStatus): Int = when (status) {
    DetectionStatus.ACTIVE -> R.string.status_detection_active
    DetectionStatus.RECOVERING -> R.string.status_detection_recovering
    DetectionStatus.STALLED -> R.string.status_detection_stalled
    DetectionStatus.NO_SENSOR -> R.string.status_detection_no_sensor
    DetectionStatus.INACTIVE -> R.string.status_detection_paused
}
