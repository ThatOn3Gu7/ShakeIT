package com.shakeit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
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

/** Material's shape scale, taken from [ShakeItShapes] so both agree. */
val ShakeItShapeScale = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = ShakeItShapes.control,
    medium = ShakeItShapes.container,
    large = ShakeItShapes.panel,
    extraLarge = ShakeItShapes.hero,
)

/** Useful when a surface should have no rounding at all (the root background). */
internal val NoShape: Shape = RectangleShape
