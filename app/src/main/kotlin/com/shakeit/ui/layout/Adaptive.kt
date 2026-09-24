package com.shakeit.ui.layout

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material's window width classes, computed from the width a screen actually has.
 *
 * Deliberately not a dependency: the app needs three buckets and a few paddings,
 * and `BoxWithConstraints` already measures the only number that matters. The
 * thresholds are Material's own (600dp and 840dp) so the breakpoints mean what
 * they mean everywhere else.
 *
 * Measuring the *window* rather than the screen orientation is the point. A
 * phone in landscape, a folded device half-open, a tablet in split screen and a
 * desktop-sized window all land in the right bucket without any of them being
 * asked what they are.
 */
enum class WindowWidth {
    /** Under 600dp: a phone in portrait, or a narrow split-screen pane. */
    Compact,

    /** 600–839dp: a large phone, a small tablet, or a phone in landscape. */
    Medium,

    /** 840dp and up: a tablet, a foldable opened flat, or a desktop window. */
    Expanded,
    ;

    /** Whether there is room to place two panes side by side. */
    val isWide: Boolean get() = this != Compact
}

/**
 * The numbers a screen lays itself out with.
 *
 * Computed once per available width and passed down, so a screen never carries
 * its own copy of "20dp on the sides" and never disagrees with its neighbour
 * about how much air the window can afford.
 *
 * @param widthClass which bucket the window fell into
 * @param availableWidth the width the screen's content box has, insets applied
 * @param gutter horizontal padding at the screen edge. Grows with the window,
 *   because 20dp of margin on a tablet is not margin, it is an accident
 * @param sectionGap vertical rhythm between sections
 * @param innerGap spacing inside a section, between rows and controls
 * @param contentWidth the width the content column is allowed to take. Capped so
 *   that a large window produces generous space rather than a 900dp-wide button
 *   with a short sentence in the middle of it
 */
@Immutable
data class ScreenMetrics(
    val widthClass: WindowWidth,
    val availableWidth: Dp,
    val gutter: Dp,
    val sectionGap: Dp,
    val innerGap: Dp,
    val contentWidth: Dp,
) {
    val twoPane: Boolean get() = widthClass == WindowWidth.Expanded
    val isCompact: Boolean get() = widthClass == WindowWidth.Compact
}

/** Content stops growing here, whatever the window does. */
private val MaxContentWidth = 640.dp

/**
 * Two-pane layouts get a wider budget: each pane is individually capped at
 * [MaxContentWidth], and the pair should not be squeezed into one column's width.
 */
private val MaxTwoPaneWidth = 1_080.dp

private val CompactWidth = 600.dp
private val MediumWidth = 840.dp

/** Maps an available width onto [ScreenMetrics]. Pure, so it can be reasoned about. */
fun metricsFor(availableWidth: Dp): ScreenMetrics {
    val widthClass = when {
        availableWidth < CompactWidth -> WindowWidth.Compact
        availableWidth < MediumWidth -> WindowWidth.Medium
        else -> WindowWidth.Expanded
    }
    return when (widthClass) {
        WindowWidth.Compact -> ScreenMetrics(
            widthClass = widthClass,
            availableWidth = availableWidth,
            gutter = 20.dp,
            sectionGap = 18.dp,
            innerGap = 12.dp,
            contentWidth = availableWidth,
        )

        WindowWidth.Medium -> ScreenMetrics(
            widthClass = widthClass,
            availableWidth = availableWidth,
            gutter = 28.dp,
            sectionGap = 22.dp,
            innerGap = 14.dp,
            contentWidth = minOf(availableWidth, MaxContentWidth),
        )

        WindowWidth.Expanded -> ScreenMetrics(
            widthClass = widthClass,
            availableWidth = availableWidth,
            gutter = 32.dp,
            sectionGap = 26.dp,
            innerGap = 16.dp,
            contentWidth = minOf(availableWidth, MaxTwoPaneWidth),
        )
    }
}

/**
 * How wide a single-column pane may get before it needs a companion.
 *
 * Used by the two-pane layouts to keep each pane inside a readable measure
 * instead of letting a row of settings stretch across a tablet.
 */
val MaxPaneWidth: Dp = 520.dp
