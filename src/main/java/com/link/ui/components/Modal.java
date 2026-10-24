package com.link.ui.components;

import javafx.animation.FadeTransition;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Lightweight modal host. Callers {@link #open(Node)} a dialog's content,
 * which is shown centered over a dimmed, fade-animated backdrop. Clicking the
 * backdrop closes the dialog. The dialog content is responsible for its own
 * buttons and for calling {@link #close()} when done.
 */
public final class Modal {

    private final StackPane root;
    private StackPane backdrop;
    private Runnable onClose;

    public Modal(StackPane root) {
        this.root = root;
    }

    public void open(Node content) {
        backdrop = new StackPane(content);
        backdrop.getStyleClass().add("dialog-overlay");
        backdrop.setAlignment(javafx.geometry.Pos.CENTER);
        backdrop.setOpacity(0);
        // Click on the dimmed backdrop (not the content) closes the dialog.
        backdrop.setOnMousePressed(e -> close());
        content.setOnMousePressed(e -> e.consume());
        root.getChildren().add(backdrop);
        FadeTransition in = new FadeTransition(Duration.millis(150), backdrop);
        in.setToValue(1);
        in.play();
    }

    
public void close() {
    if (backdrop == null) return;

    StackPane closingBackdrop = backdrop;

    FadeTransition out =
            new FadeTransition(Duration.millis(120), closingBackdrop);

    out.setToValue(0);
    out.setOnFinished(e ->
            root.getChildren().remove(closingBackdrop));

    out.play();

    closingBackdrop.setOnMousePressed(null);
    backdrop = null;

    if (onClose != null) onClose.run();
}


    public void setOnClose(Runnable r) { this.onClose = r; }

    public boolean isOpen() { return backdrop != null; }
}
