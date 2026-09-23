package com.shakeit.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The `.screen` container both destinations share.
 *
 * The prototype's `.screen` is `position: absolute; inset: 0` with
 * `padding: 18px 20px calc(20px + env(safe-area-inset-bottom))`, and its
 * `.topbar` adds `padding-top: env(safe-area-inset-top)`. Applying the system
 * bar insets first and the fixed 18/20/20 padding on top reproduces exactly
 * that, while `.screen-clip`'s background keeps painting behind the status and
 * navigation bars because the screens themselves stay transparent.
 */
@Composable
fun ShakeItScreen(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
        content = content,
    )
}
