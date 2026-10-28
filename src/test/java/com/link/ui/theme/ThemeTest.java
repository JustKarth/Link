package com.link.ui.theme;

import javafx.scene.paint.Color;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Theme#soft(Color, double)}.
 *
 * <p>The method clamps opacity to [0.0, 1.0] before creating a JavaFX Color,
 * preventing {@code IllegalArgumentException} from the animation layer when
 * boost multipliers (e.g. {@code alpha * 2.5}) produce values above 1.0.
 */
class ThemeTest {

    // Reference colour: pure red, fully opaque.
    private static final Color RED = Color.color(1.0, 0.0, 0.0, 1.0);

    // ── Valid range ────────────────────────────────────────────────────────────

    @Test
    void soft_zeroOpacity_returnsTransparent() {
        Color c = Theme.soft(RED, 0.0);
        assertEquals(0.0, c.getOpacity(), 1e-9);
        // RGB components must be preserved
        assertEquals(1.0, c.getRed(),   1e-9);
        assertEquals(0.0, c.getGreen(), 1e-9);
        assertEquals(0.0, c.getBlue(),  1e-9);
    }

    @Test
    void soft_oneOpacity_returnsFullyOpaque() {
        Color c = Theme.soft(RED, 1.0);
        assertEquals(1.0, c.getOpacity(), 1e-9);
    }

    @Test
    void soft_midOpacity_preservedExactly() {
        Color c = Theme.soft(RED, 0.42);
        // JavaFX Color stores components as float internally; tolerate float rounding.
        assertEquals(0.42, c.getOpacity(), 1e-6);
    }

    // ── Clamping — above 1.0 (the crash case) ─────────────────────────────────

    @Test
    void soft_opacityAboveOne_clampedToOne() {
        // Reported value: 0.92 * 2.0 = 1.84
        Color c = Theme.soft(RED, 1.84);
        assertEquals(1.0, c.getOpacity(), 1e-9);
    }

    @Test
    void soft_opacityAt2point5x_clampedToOne() {
        // Worst-case multiplier in drawFileTile: alpha * 2.5
        Color c = Theme.soft(RED, 0.92 * 2.5);   // 2.3
        assertEquals(1.0, c.getOpacity(), 1e-9);
    }

    @Test
    void soft_slightlyAboveOne_clampedToOne() {
        Color c = Theme.soft(RED, 1.0001);
        assertEquals(1.0, c.getOpacity(), 1e-9);
    }

    @Test
    void soft_noExceptionForOverflowValues() {
        // Must not throw IllegalArgumentException
        assertDoesNotThrow(() -> Theme.soft(RED, 1.84));
        assertDoesNotThrow(() -> Theme.soft(RED, 2.5));
        assertDoesNotThrow(() -> Theme.soft(RED, Double.MAX_VALUE));
    }

    // ── Clamping — below 0.0 ──────────────────────────────────────────────────

    @Test
    void soft_negativeOpacity_clampedToZero() {
        Color c = Theme.soft(RED, -0.1);
        assertEquals(0.0, c.getOpacity(), 1e-9);
    }

    @Test
    void soft_noExceptionForNegativeValue() {
        assertDoesNotThrow(() -> Theme.soft(RED, -1.0));
    }

    // ── RGB preservation ───────────────────────────────────────────────────────

    @Test
    void soft_rgbComponentsUnchangedAfterClamping() {
        Color base = Color.color(0.2, 0.5, 0.8, 1.0);
        Color c    = Theme.soft(base, 3.0); // clamped to 1.0
        // JavaFX Color stores components as float internally; tolerate float rounding.
        assertEquals(0.2, c.getRed(),   1e-6);
        assertEquals(0.5, c.getGreen(), 1e-6);
        assertEquals(0.8, c.getBlue(),  1e-6);
        assertEquals(1.0, c.getOpacity(), 1e-9);
    }
}
