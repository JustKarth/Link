package com.link.persistence;

/**
 * A device entry persisted in the {@code saved_devices} table.
 * Only display/identity information is stored — no keys or secrets.
 */
public class SavedDevice {
    private final String peerId;
    private String displayName;      // mutable: user can rename
    private final String deviceType; // e.g. "LAPTOP", "PHONE"
    private final String ipAddress;
    private boolean favorite;
    private final long firstSeenMillis;
    private long lastSeenMillis;

    public SavedDevice(String peerId, String displayName, String deviceType,
                       String ipAddress, boolean favorite,
                       long firstSeenMillis, long lastSeenMillis) {
        this.peerId = peerId;
        this.displayName = displayName;
        this.deviceType = deviceType == null ? "LAPTOP" : deviceType;
        this.ipAddress = ipAddress == null ? "" : ipAddress;
        this.favorite = favorite;
        this.firstSeenMillis = firstSeenMillis;
        this.lastSeenMillis = lastSeenMillis;
    }

    public String getPeerId()          { return peerId; }
    public String getDisplayName()     { return displayName; }
    public String getDeviceType()      { return deviceType; }
    public String getIpAddress()       { return ipAddress; }
    public boolean isFavorite()        { return favorite; }
    public long getFirstSeenMillis()   { return firstSeenMillis; }
    public long getLastSeenMillis()    { return lastSeenMillis; }

    public void setDisplayName(String name) { this.displayName = name; }
    public void setFavorite(boolean fav)    { this.favorite = fav; }
    public void setLastSeenMillis(long ms)  { this.lastSeenMillis = ms; }
}
