package com.link.ui.views;

/**
 * A navigable screen. Views re-read the shared prototype state on
 * {@link #refresh()} so they always reflect the latest demo data and settings.
 */
public interface View {
    /** Rebuild or update this view from the current prototype state. */
    void refresh();

    /** Human-readable title shown in the content header. */
    String title();

    /** Short subtitle shown under the title. */
    String subtitle();

    /** Called by the navigator when this view becomes the active screen. */
    default void onShow() {}

    /** Called by the navigator when this view is navigated away from. */
    default void onHide() {}
}
