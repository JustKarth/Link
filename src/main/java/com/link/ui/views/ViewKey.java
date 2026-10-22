package com.link.ui.views;

/** The four navigable screens of the prototype. */
public enum ViewKey {
    NEARBY("Nearby"),
    TRANSFERS("Transfers"),
    ACTIVITY("Activity"),
    SETTINGS("Settings");

    private final String label;
    ViewKey(String label) { this.label = label; }
    public String label() { return label; }
}
