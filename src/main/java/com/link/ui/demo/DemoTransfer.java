package com.link.ui.demo;

import java.util.Objects;

/**
 * A single in-flight or completed demo transfer of one file between this
 * device and a peer. Progress, status and simulated speed are mutated in
 * memory by the prototype's transfer timeline to drive the Transfers screen.
 *
 * <p>This is <strong>simulated</strong> data — no bytes are actually moved
 * over the network.
 */
public class DemoTransfer {

    private final String id;
    private final String fileName;
    private final long fileSize;            // bytes
    private final String fileExtension;     // e.g. "pdf", "png"
    private final TransferDirection direction;
    private final String deviceName;        // peer device display name
    private final long startedAtMillis;

    private TransferStatus status;
    private double progress;                // 0.0 .. 1.0
    private long speedBytesPerSecond;       // simulated throughput (0 when not active)
    private long completedAtMillis;         // 0 while not completed

    public DemoTransfer(String id, String fileName, long fileSize, String fileExtension,
                        TransferDirection direction, String deviceName,
                        TransferStatus status, double progress, long startedAtMillis) {
        this.id = Objects.requireNonNull(id, "id");
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        this.fileSize = fileSize;
        this.fileExtension = fileExtension == null ? "" : fileExtension;
        this.direction = Objects.requireNonNull(direction, "direction");
        this.deviceName = Objects.requireNonNull(deviceName, "deviceName");
        this.status = Objects.requireNonNull(status, "status");
        this.progress = Math.max(0.0, Math.min(1.0, progress));
        this.startedAtMillis = startedAtMillis;
    }

    public String getId() { return id; }
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public String getFileExtension() { return fileExtension; }
    public TransferDirection getDirection() { return direction; }
    public String getDeviceName() { return deviceName; }
    public long getStartedAtMillis() { return startedAtMillis; }

    public TransferStatus getStatus() { return status; }
    public void setStatus(TransferStatus status) { this.status = Objects.requireNonNull(status); }

    public double getProgress() { return progress; }
    public void setProgress(double progress) { this.progress = Math.max(0.0, Math.min(1.0, progress)); }

    public long getSpeedBytesPerSecond() { return speedBytesPerSecond; }
    public void setSpeedBytesPerSecond(long speed) { this.speedBytesPerSecond = speed; }

    public long getCompletedAtMillis() { return completedAtMillis; }
    public void setCompletedAtMillis(long t) { this.completedAtMillis = t; }

    /** True when the transfer is in a state where progress can still change. */
    public boolean isTerminal() {
        return status == TransferStatus.COMPLETED
                || status == TransferStatus.FAILED
                || status == TransferStatus.CANCELLED;
    }
}
