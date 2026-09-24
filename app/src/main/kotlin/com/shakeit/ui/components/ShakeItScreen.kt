package com.shakeit.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.shakeit.ui.layout.ScreenMetrics
import com.shakeit.ui.layout.metricsFor

/**
 * The container both screens live in.
 *
 * Three jobs, in order:
 *
 *  1. **Clear the system bars, once.** The activity draws edge to edge, so the
 *     background reaches behind the status and navigation bars; content must not.
 *     The insets are consumed here and nowhere else, which is what stops the
 *     nested-padding bug where a screen insets itself and then a card inside it
 *     insets again.
 *  2. **Measure the window** and hand the result to the screen as
 *     [ScreenMetrics], so a screen adapts to the space it was given instead of
 *     assuming a phone's width.
 *  3. **Cap the measure.** The content column is centred and never wider than
 *     [ScreenMetrics.contentWidth]. A settings row stretched across a tablet is
 *     not adaptive layout, it is a layout that stopped caring at 400dp.
 *
 * Vertical padding is the screen's own decision: the home screen wants its hero
 * to breathe against the bar insets, the settings screen wants its title tight to
 * the top.
 */
@Composable
fun ShakeItScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.(ScreenMetrics) -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        val metrics = metricsFor(maxWidth)

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = metrics.contentWidth)
                    .padding(horizontal = metrics.gutter),
                content = { content(metrics) },
            )
        }
    }
}
