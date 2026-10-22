package com.link.ui.components;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.util.Duration;

/**
 * A compact, macOS-style toggle switch. Built from styled Regions so it
 * matches the theme via CSS classes {@code toggle} / {@code toggle on} and
 * {@code toggle-thumb}. Animates the thumb slide on the JavaFX Application
 * Thread.
 */
public class ToggleSwitch extends StackPane {

    private final Region thumb = new Region();
    private boolean on = false;
    private Consumer onChange;

    public ToggleSwitch() {
        getStyleClass().add("toggle");
        thumb.getStyleClass().add("toggle-thumb");
        thumb.setMouseTransparent(true);

        // Stack thumb and keep it left-aligned; we translate it manually.
        setAlignment(thumb, javafx.geometry.Pos.CENTER_LEFT);
        setTranslateXForThumb(false);

        getChildren().add(thumb);

        setOnMouseClicked(e -> toggle());
        setOnMousePressed(e -> e.consume());
    }

    private void setTranslateXForThumb(boolean on) {
        // Width is fixed via CSS (44); thumb width ~20; padding keeps it inset.
        thumb.setTranslateX(on ? 20 : 0);
    }

    public boolean isOn() { return on; }

    public void setOn(boolean on, boolean fire) {
        boolean changed = this.on != on;
        this.on = on;
        updateStyle();
        animateThumb(on);
        if (changed && fire && onChange != null) onChange.accept(on);
    }

    public void toggle() {
        setOn(!on, true);
    }

    private void updateStyle() {
        if (on) {
            if (!getStyleClass().contains("on")) getStyleClass().add("on");
        } else {
            getStyleClass().remove("on");
        }
    }

    private void animateThumb(boolean toOn) {
        double from = toOn ? 0 : 20;
        double to = toOn ? 20 : 0;
        Timeline t = new Timeline();
        // Animate the thumb translateX for a small spring.
        javafx.animation.KeyValue kv0 = new javafx.animation.KeyValue(thumb.translateXProperty(), from);
        javafx.animation.KeyValue kv1 = new javafx.animation.KeyValue(thumb.translateXProperty(), to);
        t.getKeyFrames().addAll(new KeyFrame(Duration.ZERO, kv0), new KeyFrame(Duration.millis(130), kv1));
        t.play();
    }

    /** Callback receiving the new on/off state. */
    public void setOnChange(Consumer onChange) { this.onChange = onChange; }

    /** Tiny functional interface so we don't pull in java.util.function only for this. */
    @FunctionalInterface
    public interface Consumer { void accept(boolean on); }
}
