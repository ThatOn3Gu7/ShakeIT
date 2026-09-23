package com.shakeit

import org.junit.Assert.assertTrue
import kotlin.math.abs

/**
 * Float-safe `assertEquals`.
 *
 * JUnit 4 ships `assertEquals(double, double, double)` but no `Float` overload,
 * and Kotlin does not widen `Float` to `Double` implicitly, so the three-argument
 * form would silently resolve to `assertEquals(String, Object, Object)` and drop
 * the delta. Comparing by hand keeps the tolerance explicit.
 */
fun assertClose(
    expected: Float,
    actual: Float,
    delta: Float = 1e-4f,
    message: String? = null,
) {
    val difference = abs(expected - actual)
    assertTrue(
        message?.let { "$it: " }.orEmpty() +
            "expected <$expected> but was <$actual> (off by $difference, tolerance $delta)",
        difference <= delta,
    )
}

/** The same check for `Double` reference values computed inside a test. */
fun assertClose(
    expected: Double,
    actual: Double,
    delta: Double = 1e-9,
    message: String? = null,
) {
    val difference = abs(expected - actual)
    assertTrue(
        message?.let { "$it: " }.orEmpty() +
            "expected <$expected> but was <$actual> (off by $difference, tolerance $delta)",
        difference <= delta,
    )
}
