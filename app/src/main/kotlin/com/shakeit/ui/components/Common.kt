package com.shakeit.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics

/**
 * Sets a spoken label for the node and merges whatever its children would have
 * contributed.
 *
 * `SemanticsProperties.ContentDescription` is keyed as a *list* of strings, so
 * it is written through `SemanticsPropertyReceiver.set` rather than as a plain
 * assignment.
 */
internal fun Modifier.accessibilityLabel(label: String): Modifier =
    semantics(mergeDescendants = true) {
        set(SemanticsProperties.ContentDescription, listOf(label))
    }

/** `transition: background .15s` / `color .15s` on the prototype's controls. */
internal const val PRESS_TRANSITION_MS = 150

/** `.switch { transition: background .2s }` and `.knob { transition: transform .2s }`. */
internal const val SWITCH_TRANSITION_MS = 200
