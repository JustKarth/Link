package com.link.ui.views;

import javafx.scene.layout.StackPane;

/**
 * Provided to views so they can navigate between screens and access the
 * top-level overlay layer used to host modal dialogs.
 */
public interface Navigator {
    void goTo(ViewKey key);
    /** The root layer to which modals are added (covers the whole window). */
    StackPane overlayRoot();
}
