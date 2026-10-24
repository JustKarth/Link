package com.link.ui.components;

import com.link.ui.theme.Theme;
import com.link.ui.util.FormatUtil;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;

/**
 * Hand-authored vector icon set for the prototype. Each icon is drawn on a
 * 24×24 grid and wrapped in a fixed-size {@link StackPane} with an explicit
 * centered {@link Scale} so the icon always occupies exactly the requested
 * size for layout, regardless of JavaFX bounds semantics.
 *
 * <p>Icons return plain {@link Node}s (no text), coloured in code via the
 * {@link Theme} palette. Callers can recolour by setting the icon's stroke/fill
 * on the returned path — but the convenience methods already theme them.
 */
public final class Icons {

    private Icons() {}

    private static final double BASE = 24.0;
    private static final double STROKE = 1.7;
    private static final double STROKE_HEAVY = 2.0;

    // ---- Core helpers -----------------------------------------------------

    /** Stroke-style icon (outline), wrapped to a fixed-size pane. */
    private static Node stroke(String content, double size, Color color, double strokeWidth) {
        SVGPath p = new SVGPath();
        p.setContent(content);
        p.setFill(null);
        p.setStroke(color == null ? Theme.TEXT_2 : color);
        p.setStrokeWidth(strokeWidth);
        p.setStrokeLineCap(StrokeLineCap.ROUND);
        p.setStrokeLineJoin(StrokeLineJoin.ROUND);
        return wrap(p, size);
    }

    /** Fill-style icon (solid), wrapped to a fixed-size pane. */
    private static Node fill(String content, double size, Color color) {
        SVGPath p = new SVGPath();
        p.setContent(content);
        p.setFill(color == null ? Theme.TEXT_2 : color);
        p.setStroke(null);
        return wrap(p, size);
    }

    /** Scale a 24×24-designed node to the requested size and wrap it so the
     *  layout slot equals the requested size. */
    private static Node wrap(Node node, double size) {
        double s = size / BASE;
        if (s != 1.0) {
            node.getTransforms().add(new Scale(s, s, BASE / 2.0, BASE / 2.0));
        }
        StackPane pane = new StackPane(node);
        pane.setMinSize(size, size);
        pane.setPrefSize(size, size);
        pane.setMaxSize(size, size);
        pane.setMouseTransparent(true);
        pane.getStyleClass().add("icon-pane");
        return pane;
    }

    /** Recolour a previously-created icon (used for nav active state). */
    public static void recolor(Node icon, Color color) {
        recolorIn(icon, color);
    }

    private static void recolorIn(Node n, Color color) {
        if (n instanceof SVGPath p) {
            if (p.getStroke() != null) p.setStroke(color);
            else p.setFill(color);
        } else if (n instanceof Group g) {
            for (Node c : g.getChildren()) recolorIn(c, color);
        } else if (n instanceof javafx.scene.layout.Pane pane) {
            for (Node c : pane.getChildren()) recolorIn(c, color);
        }
    }

    // ---- Brand / logo -----------------------------------------------------

    public static Node brand(double size) {
        Circle center = new Circle(12, 12, 2.6, Theme.ACCENT);
        center.setMouseTransparent(true);

        double[][] outer = {{12, 5}, {5, 16.5}, {19, 16.5}};
        Group g = new Group();
        for (double[] o : outer) {
            Line ln = new Line(12, 12, o[0], o[1]);
            ln.setStroke(Theme.ACCENT);
            ln.setStrokeWidth(1.3);
            ln.setOpacity(0.45);
            ln.setMouseTransparent(true);
            g.getChildren().add(ln);
        }
        for (double[] o : outer) {
            Circle c = new Circle(o[0], o[1], 2.1);
            c.setFill(null);
            c.setStroke(Theme.ACCENT_2);
            c.setStrokeWidth(1.6);
            c.setMouseTransparent(true);
            g.getChildren().add(c);
        }
        g.getChildren().add(center);
        return wrap(g, size);
    }

    // ---- Navigation -------------------------------------------------------

    public static Node nearby(double size, Color color) {
        return stroke("M12 5a7 7 0 1 0 0.01 0 M12 8.5a3.5 3.5 0 1 0 0.01 0", size, color, STROKE);
    }
    public static Node transfers(double size, Color color) {
        return stroke("M7 8h10 M14 5l3 3-3 3 M17 16H7 M10 13l-3 3 3 3", size, color, STROKE);
    }
    public static Node activity(double size, Color color) {
        return stroke("M12 5a7 7 0 1 0 0.01 0 M12 12V8 M12 12l3.2 2", size, color, STROKE);
    }
    public static Node settings(double size, Color color) {
        return stroke("M4 7h16 M4 12h16 M4 17h16 M9 5v4 M15 10v4 M7 15v4", size, color, STROKE);
    }
    public static Node devices(double size, Color color) {
        // Two overlapping device outlines — laptop behind, phone in front-right
        return stroke("M3 7h11v8H3z M3 17h11 M17 9h4v10h-4z M18.5 19.5h1", size, color, STROKE);
    }
    public static Node star(double size, Color color) {
        return stroke("M12 3l2.5 5.5 6 .9-4.3 4.2 1 6-5.2-2.8L6.5 19.6l1-6L3.2 9.4l6-.9z",
                size, color, STROKE);
    }
    public static Node starFilled(double size, Color color) {
        return fill("M12 3l2.5 5.5 6 .9-4.3 4.2 1 6-5.2-2.8L6.5 19.6l1-6L3.2 9.4l6-.9z",
                size, color);
    }
    public static Node edit(double size, Color color) {
        return stroke("M4 20h4L18.5 9.5a2.83 2.83 0 0 0-4-4L4 16v4z M14.5 5.5l4 4", size, color, STROKE);
    }
    public static Node emptyDevices(double size, Color color) {
        return stroke("M7 8h10v8H7z M5 18h14 M12 3a3 3 0 0 1 3 3", size, color, STROKE);
    }

    // ---- Device types -----------------------------------------------------

    public static Node device(double size, String type, Color color) {
        String c = switch (type == null ? "LAPTOP" : type) {
            case "LAPTOP"  -> "M6 8h12v8H6z M4 18h16";
            case "DESKTOP" -> "M5 5h14v11H5z M9 20h6 M12 16v4";
            case "PHONE"   -> "M8 3h8v18H8z M11 19h2";
            case "TABLET"  -> "M6 4h12v16H6z M11 18h2";
            default        -> "M6 8h12v8H6z M4 18h16";
        };
        return stroke(c, size, color == null ? Theme.TEXT_2 : color, STROKE);
    }

    // ---- File type (filled, tinted by category) ---------------------------

    public static Node fileIcon(double size, FormatUtil.FileCategory category) {
        Color c = Theme.colorFor(category);
        return fill("M8 3h6l4 4v14H8z M14 3v4h4", size, c);
    }

    public static Node fileIcon(double size, Color color) {
        return fill("M8 3h6l4 4v14H8z M14 3v4h4", size, color);
    }

    // ---- Actions / affordances --------------------------------------------

    public static Node send(double size, Color color) {
        return stroke("M4 12h13 M11 6l6 6-6 6", size, color, STROKE);
    }
    public static Node connect(double size, Color color) {
        return stroke("M12 4a8 8 0 1 0 0.01 0 M4 12h16 M12 4v16", size, color, STROKE);
    }
    public static Node plus(double size, Color color) {
        return stroke("M12 7v10 M7 12h10", size, color, STROKE_HEAVY);
    }
    public static Node trash(double size, Color color) {
        return stroke("M6 7h12 M9 7V4h6v3 M8 7l1 13h6l1-13 M10 11v6 M14 11v6", size, color, STROKE);
    }
    public static Node close(double size, Color color) {
        return stroke("M6 6l12 12 M18 6L6 18", size, color, STROKE);
    }
    public static Node check(double size, Color color) {
        return stroke("M5 12l4 4 10-11", size, color, STROKE_HEAVY);
    }
    public static Node warning(double size, Color color) {
        return stroke("M12 4l9 16H3z M12 10v4 M11.8 17h0.4", size, color, STROKE);
    }
    public static Node info(double size, Color color) {
        return stroke("M12 4a8 8 0 1 0 0.01 0 M12 11v5 M11.8 8h0.4", size, color, STROKE);
    }
    public static Node search(double size, Color color) {
        return stroke("M11 5a6 6 0 1 0 0.01 0 M16.2 16.2l4.3 4.3", size, color, STROKE);
    }
    public static Node folder(double size, Color color) {
        return stroke("M4 7h6l2 2h8v10H4z", size, color, STROKE);
    }
    public static Node chevronRight(double size, Color color) {
        return stroke("M9 6l6 6-6 6", size, color, STROKE);
    }
    public static Node arrowUp(double size, Color color) {
        return stroke("M12 19V5 M6 11l6-6 6 6", size, color, STROKE);
    }
    public static Node arrowDown(double size, Color color) {
        return stroke("M12 5v14 M6 13l6 6 6-6", size, color, STROKE);
    }
    public static Node pause(double size, Color color) {
        return stroke("M9 6v12 M15 6v12", size, color, STROKE_HEAVY);
    }
    public static Node play(double size, Color color) {
        return stroke("M8 5l11 7-11 7z", size, color, STROKE);
    }
    public static Node cancel(double size, Color color) {
        return stroke("M12 4a8 8 0 1 0 0.01 0 M9.5 9.5l5 5 M14.5 9.5l-5 5", size, color, STROKE);
    }

    // ---- Empty / state ----------------------------------------------------

    public static Node emptyNearby(double size, Color color) {
        return stroke("M12 5a7 7 0 1 0 0.01 0 M12 9a3 3 0 1 0 0.01 0 M5 5l14 14", size, color, STROKE);
    }
    public static Node emptyTransfers(double size, Color color) {
        return transfers(size, color);
    }
    public static Node emptyActivity(double size, Color color) {
        return activity(size, color);
    }
    public static Node noResults(double size, Color color) {
        return stroke("M11 5a6 6 0 1 0 0.01 0 M16.2 16.2l4.3 4.3 M8 8l6 6 M14 8L8 14", size, color, STROKE);
    }
}
