package com.link.ui.demo;

import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Application settings, backed by Java {@link Preferences} so values survive
 * restarts. Each setter immediately flushes to the backing store.
 *
 * <p>The preference node is per-user and scoped to this class, so settings
 * are isolated from other applications on the same machine.
 */
public class SettingsModel {

    private static final String KEY_NAME          = "deviceDisplayName";
    private static final String KEY_DISCOVERY      = "discoveryEnabled";
    private static final String KEY_DISCOVERABLE   = "discoverable";
    private static final String KEY_DOWNLOAD_DIR   = "downloadDestination";
    private static final String KEY_REQUIRE_APPR   = "requireApproval";
    private static final String KEY_NOTIFICATIONS  = "desktopNotifications";

    private static final String DEFAULT_DOWNLOAD_DIR =
            System.getProperty("user.home") + "/Downloads";

    private final Preferences prefs =
            Preferences.userNodeForPackage(SettingsModel.class);

    // ── Cached values (loaded once, mutated by setters) ───────────────────────

    private String  deviceDisplayName;
    private boolean mockDiscoveryEnabled;
    private boolean discoverable;
    private String  downloadDestination;
    private boolean requireApprovalForIncoming;
    private boolean desktopNotifications;

    public SettingsModel() {
        // Load from persistent store; fall back to sensible defaults.
        deviceDisplayName           = prefs.get(KEY_NAME, resolveDefaultName());
        mockDiscoveryEnabled        = prefs.getBoolean(KEY_DISCOVERY,     true);
        discoverable                = prefs.getBoolean(KEY_DISCOVERABLE,  true);
        downloadDestination         = prefs.get(KEY_DOWNLOAD_DIR, DEFAULT_DOWNLOAD_DIR);
        requireApprovalForIncoming  = prefs.getBoolean(KEY_REQUIRE_APPR,  true);
        desktopNotifications        = prefs.getBoolean(KEY_NOTIFICATIONS,  true);
    }

    // ── General ───────────────────────────────────────────────────────────────

    public String getDeviceDisplayName() { return deviceDisplayName; }

    public void setDeviceDisplayName(String name) {
        this.deviceDisplayName = Objects.requireNonNullElse(name, "This Device");
        prefs.put(KEY_NAME, this.deviceDisplayName);
        flush();
    }

    // ── Discovery ─────────────────────────────────────────────────────────────

    public boolean isMockDiscoveryEnabled() { return mockDiscoveryEnabled; }

    public void setMockDiscoveryEnabled(boolean v) {
        this.mockDiscoveryEnabled = v;
        prefs.putBoolean(KEY_DISCOVERY, v);
        flush();
    }

    public boolean isDiscoverable() { return discoverable; }

    public void setDiscoverable(boolean v) {
        this.discoverable = v;
        prefs.putBoolean(KEY_DISCOVERABLE, v);
        flush();
    }

    // ── Transfers ─────────────────────────────────────────────────────────────

    public String getDownloadDestination() { return downloadDestination; }

    public void setDownloadDestination(String v) {
        if (v != null && !v.isBlank()) {
            this.downloadDestination = v;
            prefs.put(KEY_DOWNLOAD_DIR, v);
            flush();
        }
    }

    public boolean isRequireApprovalForIncoming() { return requireApprovalForIncoming; }

    public void setRequireApprovalForIncoming(boolean v) {
        this.requireApprovalForIncoming = v;
        prefs.putBoolean(KEY_REQUIRE_APPR, v);
        flush();
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    public boolean isDesktopNotifications() { return desktopNotifications; }

    public void setDesktopNotifications(boolean v) {
        this.desktopNotifications = v;
        prefs.putBoolean(KEY_NOTIFICATIONS, v);
        flush();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String resolveDefaultName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (java.net.UnknownHostException e) {
            return System.getProperty("user.name", "This Device") + "'s Device";
        }
    }

    private void flush() {
        try { prefs.flush(); } catch (Exception ignored) {}
    }
}
