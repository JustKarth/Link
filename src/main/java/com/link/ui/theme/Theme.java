package com.link.ui.theme;

import com.link.ui.util.FormatUtil;
import javafx.scene.paint.Color;

import java.util.Map;

/**
 * Java-side mirror of the CSS design tokens in theme.css, plus helpers for
 * per-category file colours. UI code uses these when it must set colours
 * programmatically (shapes, dynamic tints). Everything else is styled via CSS.
 */
public final class Theme {

    private Theme() {}

    // Surfaces
    public static final Color BG          = Color.web("#0B0F14");
    public static final Color PANEL       = Color.web("#121922");
    public static final Color PANEL_2     = Color.web("#161E29");
    public static final Color PANEL_3     = Color.web("#1C2733");
    public static final Color BORDER      = Color.web("#253140");
    public static final Color BORDER_SOFT = Color.web("#1B2733");

    // Accents
    public static final Color ACCENT   = Color.web("#50E6FF");
    public static final Color ACCENT_2 = Color.web("#9B87FF");

    // Text
    public static final Color TEXT   = Color.web("#F2F5F8");
    public static final Color TEXT_2 = Color.web("#8D9AAA");
    public static final Color TEXT_3 = Color.web("#5E6B7A");

    // Semantic
    public static final Color SUCCESS = Color.web("#41C9A2");
    public static final Color WARNING = Color.web("#F5B664");
    public static final Color ERROR   = Color.web("#EC6A6B");

    /** A soft tint of a colour at the given opacity, clamped to [0.0, 1.0]. */
    public static Color soft(Color c, double alpha) {
        double a = Math.max(0.0, Math.min(1.0, alpha));
        return Color.color(c.getRed(), c.getGreen(), c.getBlue(), a);
    }

    /** CSS rgba() string for inline styling of dynamically-coloured Regions. */
    public static String toRgba(Color c, double alpha) {
        return String.format("rgba(%d,%d,%d,%.3f)",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255),
                alpha);
    }
    public static String toRgba(Color c) { return toRgba(c, c.getOpacity()); }

    public static Color accentSoft()  { return soft(ACCENT, 0.14); }
    public static Color accent2Soft() { return soft(ACCENT_2, 0.14); }

    /** Stroke/file colour for a given file category. */
    public static Color colorFor(FormatUtil.FileCategory c) {
        return switch (c) {
            case IMAGE    -> ACCENT_2;
            case VIDEO    -> ACCENT;
            case AUDIO    -> SUCCESS;
            case ARCHIVE  -> WARNING;
            case CODE     -> Color.web("#6FB7FF");
            case DOCUMENT -> TEXT_2;
            default       -> TEXT_2;
        };
    }

    /** Soft background for a file-type chip wrap, given its category. */
    public static Color softFor(FormatUtil.FileCategory c) {
        return soft(colorFor(c), 0.14);
    }

    /** Map a status name to a colour (for status dots / pills drawn in code). */
    public static Color statusColor(String status) {
        return switch (status) {
            case "online" -> SUCCESS;
            case "away"   -> WARNING;
            case "offline", "paused", "cancelled" -> TEXT_3;
            case "completed" -> SUCCESS;
            case "failed" -> ERROR;
            case "in-progress", "pending", "in_progress" -> ACCENT;
            default -> TEXT_2;
        };
    }
}
