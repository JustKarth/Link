package com.link.ui.demo;

import java.util.Objects;

/**
 * In-memory prototype settings. The Settings screen reads and mutates this
 * object directly. Nothing here is persisted — settings reset when the
 * prototype restarts (documented in the README). A future backend can replace
 * this with a persisted preference store without touching the UI.
 */
public class SettingsModel {

    // --- General ----------------------------------------------------------
    private String deviceDisplayName;

    // --- Discovery -------------------------------------------------------
    private boolean mockDiscoveryEnabled = true;
    private boolean discoverable = true;

    // --- Transfers -------------------------------------------------------
    private String downloadDestination = System.getProperty("user.home") + "/Downloads";
    private boolean requireApprovalForIncoming = true;

    // --- Notifications ----------------------------------------------------
    private boolean desktopNotifications = true;

    public String getDeviceDisplayName() { return deviceDisplayName; }
    public void setDeviceDisplayName(String deviceDisplayName) {
        this.deviceDisplayName = Objects.requireNonNullElse(deviceDisplayName, "This Device");
    }

    public boolean isMockDiscoveryEnabled() { return mockDiscoveryEnabled; }
    public void setMockDiscoveryEnabled(boolean v) { this.mockDiscoveryEnabled = v; }

    public boolean isDiscoverable() { return discoverable; }
    public void setDiscoverable(boolean v) { this.discoverable = v; }

    public String getDownloadDestination() { return downloadDestination; }
    public void setDownloadDestination(String v) {
        this.downloadDestination = (v == null || v.isBlank()) ? downloadDestination : v;
    }

    public boolean isRequireApprovalForIncoming() { return requireApprovalForIncoming; }
    public void setRequireApprovalForIncoming(boolean v) { this.requireApprovalForIncoming = v; }

    public boolean isDesktopNotifications() { return desktopNotifications; }
    public void setDesktopNotifications(boolean v) { this.desktopNotifications = v; }
}
