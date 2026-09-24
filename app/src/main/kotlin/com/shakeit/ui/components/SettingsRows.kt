package com.shakeit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shakeit.ui.theme.ShakeItShapes

/* The row grid. One set of numbers, so every row in the app lines up. */
private val RowPadding = 16.dp
private val TileSize = 36.dp
private val TileGap = 16.dp
private val TextColumnStart = RowPadding + TileSize + TileGap
private val RowMinHeight = 56.dp

/** A button may not become a banner on a wide window. */
private val MaxActionWidth = 420.dp

/**
 * The words above a group of settings.
 *
 * Uppercased at the call site's string rather than here, because the strings are
 * resources and the shape of the words belongs to the language, not to the layout.
 * `labelLarge` in `primary` on the screen background: the section is named, not
 * boxed, which is what keeps the page from becoming a stack of identical cards.
 */
@Composable
fun SettingsSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    support: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (support != null) {
            Text(
                text = support,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * A tonal panel that holds a group of rows.
 *
 * Not every group gets one: two controls that belong together can be held by
 * whitespace alone, and a page where every section is the same rounded rectangle
 * is a page with no hierarchy. Panels are used where a group has enough rows to
 * need an edge.
 *
 * @param color one step of the `surfaceContainer` ladder. The step is chosen per
 *   group so nested content can go one lighter or darker and still read as
 *   inside, without a border or a shadow anywhere.
 */
@Composable
fun SettingsPanel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    shape: Shape = ShakeItShapes.panel,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier.fillMaxWidth(), shape = shape, color = color) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), content = content)
    }
}

/** The 36dp tile behind a row's leading icon. */
@Composable
private fun LeadingTile(
    icon: ImageVector,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.size(TileSize),
        shape = ShakeItShapes.control,
        color = container,
        contentColor = content,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * One row of a settings group: an icon tile, a title, an optional subtitle and
 * something on the right.
 *
 * 56dp tall at minimum, which is Material's own touch-target floor and also the
 * height at which a row stops feeling cramped next to a 36dp tile.
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleContent: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    iconContainer: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconContent: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .padding(horizontal = RowPadding, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingTile(icon = icon, container = iconContainer, content = iconContent)
        Spacer(Modifier.width(TileGap))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            when {
                subtitleContent != null -> subtitleContent()
                subtitle != null -> Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

/** A row with a Material switch on the right. */
@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .padding(start = RowPadding, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingTile(
            icon = icon,
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.width(TileGap))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/**
 * A row whose control lives underneath it — a slider, a segmented row, buttons.
 *
 * @param alignToText whether the control starts at the text column (68dp) or at
 *   the row's own padding. Sliders align to the text, because a slider that
 *   starts under the icon looks misaligned next to the words it belongs to;
 *   segmented rows take the full width, because three options need the room.
 */
@Composable
fun SettingsControlRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleContent: (@Composable () -> Unit)? = null,
    alignToText: Boolean = true,
    control: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        SettingsRow(
            icon = icon,
            title = title,
            subtitle = subtitle,
            subtitleContent = subtitleContent,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = if (alignToText) TextColumnStart else RowPadding,
                    end = RowPadding,
                    top = 4.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = control,
        )
    }
}

/**
 * A read-only row: a label and the fact beside it. Used by the diagnostics
 * report, where nothing is clickable and a control-shaped affordance would be a
 * promise the row cannot keep.
 */
@Composable
fun SettingsInfoRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    emphasised: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (icon == null) TextColumnStart else RowPadding,
                end = RowPadding,
                top = 10.dp,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(TileGap))
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = if (emphasised) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.labelLarge
                },
                color = if (emphasised) colors.onSurface else colors.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (emphasised) colors.onSurfaceVariant else colors.onSurface,
            )
        }
    }
}

/**
 * A button inside a settings row.
 *
 * Full width up to [MaxActionWidth]: on a phone that is the row, and on a tablet
 * it stops being a banner with a short sentence stranded in the middle of it.
 *
 * @param prominent the one action this row is really offering. Tonal rather than
 *   filled — a settings page whose every button is a solid slab of primary is a
 *   page shouting at the user about things they did not ask about.
 */
@Composable
fun SettingsActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    prominent: Boolean = false,
) {
    val buttonModifier = modifier
        .widthIn(max = MaxActionWidth)
        .fillMaxWidth()
        .heightIn(min = 48.dp)

    if (prominent) {
        FilledTonalButton(onClick = onClick, modifier = buttonModifier) {
            ButtonLabel(text = text, icon = icon)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = buttonModifier) {
            ButtonLabel(text = text, icon = icon)
        }
    }
}

@Composable
private fun ButtonLabel(text: String, icon: ImageVector?) {
    if (icon != null) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 2,
        textAlign = TextAlign.Center,
    )
}
