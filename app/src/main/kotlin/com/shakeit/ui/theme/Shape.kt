package com.shakeit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * ShakeIT's shape language.
 *
 * Five sizes, each with a job, and the differences between them are large enough
 * to read as a system rather than as rounding applied at random. Material's own
 * scale is filled in from the same numbers so stock components agree with the
 * custom ones.
 *
 * The hero is the exception on purpose: it is the app's identity, it is the one
 * surface a user touches on purpose, and it is the only thing on screen that is
 * allowed to look like nothing else.
 */
object ShakeItShapes {

    /** The hero container. Larger than anything else in the app. */
    val hero: Shape = RoundedCornerShape(44.dp)

    /** Primary surfaces: the big panels that hold a group of controls. */
    val panel: Shape = RoundedCornerShape(28.dp)

    /** Supporting containers: a row's leading tile, a nested block, a chip bed. */
    val container: Shape = RoundedCornerShape(18.dp)

    /** Controls: buttons, sliders' housing, small tiles. */
    val control: Shape = RoundedCornerShape(14.dp)

    /** Pills and chips — status, stats, anything that should read as a label. */
    val pill: Shape = RoundedCornerShape(percent = 50)

    /**
     * The shape of an informational surface that must not be mistaken for a
     * button: deliberately *not* a control shape, and never a pill.
     */
    val informational: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 28.dp, bottomEnd = 10.dp)
}

/**
 * Material's stock shape scale remains available through MaterialTheme. ShakeIT's
 * custom surfaces use the expressive roles above directly; the library's Shapes
 * constructor is internal in the resolved Material3 release, so we do not
 * duplicate or reflect it just to replace defaults.
 */
