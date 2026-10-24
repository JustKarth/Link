package com.link.ui.views;

/** The five navigable screens of the application. */
public enum ViewKey {
    NEARBY("Nearby"),
    TRANSFERS("Transfers"),
    ACTIVITY("Activity"),
    DEVICES("Devices"),
    SETTINGS("Settings");

    private final String label;
    ViewKey(String label) { this.label = label; }
    public String label() { return label; }
}
