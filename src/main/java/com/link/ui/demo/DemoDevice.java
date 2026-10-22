package com.link.ui.demo;

import java.util.Objects;

/**
 * A discovered peer device. Plain POJO — no JavaFX types — so the demo layer
 * stays cleanly separable from the presentation layer. Status is mutable so
 * the mock discovery engine can flip a device between online/away/offline.
 */
public class DemoDevice {

    private final String id;
    private final String name;
    private final DeviceType type;
    private final String ipAddress;
    private final String osLabel;
    private DeviceStatus status;

    public DemoDevice(String id, String name, DeviceType type, String ipAddress,
                      String osLabel, DeviceStatus status) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.ipAddress = ipAddress == null ? "" : ipAddress;
        this.osLabel = osLabel == null ? "" : osLabel;
        this.status = Objects.requireNonNull(status, "status");
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public DeviceType getType() { return type; }
    public String getIpAddress() { return ipAddress; }
    public String getOsLabel() { return osLabel; }
    public DeviceStatus getStatus() { return status; }

    public void setStatus(DeviceStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }
}
