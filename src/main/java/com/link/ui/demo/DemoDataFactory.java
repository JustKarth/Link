package com.link.ui.demo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The single source of mock data for the prototype. Everything the UI shows
 * about devices, transfers and history originates here. When the real
 * backend lands, this factory (and {@link com.link.ui.prototype.PrototypeState})
 * is the seam to replace — the views themselves never hardcode demo data.
 *
 * <p>All timestamps are computed relative to {@link System#currentTimeMillis()}
 * at call time so the demo always looks "live".
 */
public final class DemoDataFactory {

    private DemoDataFactory() {}

    /** The device the user is currently running the prototype on. */
    public static DemoDevice thisDevice() {
        return new DemoDevice(
                "self",
                "Manya's MacBook Air",
                DeviceType.LAPTOP,
                "192.168.1.20",
                "macOS 15",
                DeviceStatus.ONLINE
        );
    }

    /** App version label shown in Settings. */
    public static String appVersion() {
        return "1.0.0 (demo)";
    }

    /** A small, varied set of nearby peers. */
    public static List<DemoDevice> nearbyDevices() {
        List<DemoDevice> devices = new ArrayList<>();
        devices.add(new DemoDevice("d1", "Maya's MacBook Pro", DeviceType.LAPTOP,
                "192.168.1.24", "macOS 14 Sonoma", DeviceStatus.ONLINE));
        devices.add(new DemoDevice("d2", "Studio iMac", DeviceType.DESKTOP,
                "192.168.1.31", "macOS 13 Ventura", DeviceStatus.ONLINE));
        devices.add(new DemoDevice("d3", "Pixel 8", DeviceType.PHONE,
                "192.168.1.55", "Android 14", DeviceStatus.ONLINE));
        devices.add(new DemoDevice("d4", "iPhone 15", DeviceType.PHONE,
                "192.168.1.48", "iOS 17", DeviceStatus.AWAY));
        devices.add(new DemoDevice("d5", "ThinkPad X1", DeviceType.LAPTOP,
                "192.168.1.67", "Windows 11", DeviceStatus.ONLINE));
        devices.add(new DemoDevice("d6", "iPad Pro", DeviceType.TABLET,
                "192.168.1.70", "iPadOS 17", DeviceStatus.AWAY));
        return devices;
    }

    /** Seed transfers so the Transfers screen is populated on first launch. */
    public static List<DemoTransfer> seedTransfers(long now) {
        long HOUR = 3_600_000L;
        long MIN = 60_000L;
        List<DemoTransfer> t = new ArrayList<>();

        t.add(new DemoTransfer("seed-t1", "Q3-Financial-Report.pdf", 4_823_000L, "pdf",
                TransferDirection.OUTGOING, "Maya's MacBook Pro",
                TransferStatus.COMPLETED, 1.0, now - 2 * HOUR - 12 * MIN));
        t.get(0).setCompletedAtMillis(now - 2 * HOUR + 4 * MIN);

        t.add(new DemoTransfer("seed-t2", "product-shots.zip", 62_400_000L, "zip",
                TransferDirection.INCOMING, "Studio iMac",
                TransferStatus.COMPLETED, 1.0, now - 5 * HOUR));
        t.get(1).setCompletedAtMillis(now - 4 * HOUR - 40 * MIN);

        // An active, simulated in-progress transfer.
        DemoTransfer live = new DemoTransfer("seed-t3", "keynote-v3.mov", 1_280_000_000L, "mov",
                TransferDirection.OUTGOING, "Maya's MacBook Pro",
                TransferStatus.IN_PROGRESS, 0.42, now - 90_000L);
        live.setSpeedBytesPerSecond(26_300_000L); // ~26 MB/s (simulated)
        t.add(live);

        t.add(new DemoTransfer("seed-t4", "dataset.csv", 18_900_000L, "csv",
                TransferDirection.INCOMING, "ThinkPad X1",
                TransferStatus.COMPLETED, 1.0, now - 26 * HOUR));
        t.get(3).setCompletedAtMillis(now - 25 * HOUR);

        // A failed transfer (kept partial to show the failed state).
        DemoTransfer failed = new DemoTransfer("seed-t5", "archive-2024.zip", 240_000_000L, "zip",
                TransferDirection.OUTGOING, "Pixel 8",
                TransferStatus.FAILED, 0.63, now - 30 * HOUR);
        t.add(failed);

        // A paused transfer (demo only).
        DemoTransfer paused = new DemoTransfer("seed-t6", "playlist.m4a", 8_100_000L, "m4a",
                TransferDirection.INCOMING, "iPhone 15",
                TransferStatus.PAUSED, 0.20, now - 40 * MIN);
        paused.setSpeedBytesPerSecond(0);
        t.add(paused);

        return t;
    }

    /** Seed activity history so the Activity screen has content immediately. */
    public static List<DemoActivity> seedActivities(long now) {
        long HOUR = 3_600_000L;
        long MIN = 60_000L;
        List<DemoActivity> a = new ArrayList<>();
        a.add(new DemoActivity("a1", "Q3-Financial-Report.pdf", 4_823_000L, "pdf",
                TransferDirection.OUTGOING, "Maya's MacBook Pro", true, now - 2 * HOUR + 4 * MIN));
        a.add(new DemoActivity("a2", "product-shots.zip", 62_400_000L, "zip",
                TransferDirection.INCOMING, "Studio iMac", true, now - 4 * HOUR - 40 * MIN));
        a.add(new DemoActivity("a3", "dataset.csv", 18_900_000L, "csv",
                TransferDirection.INCOMING, "ThinkPad X1", true, now - 25 * HOUR));
        a.add(new DemoActivity("a4", "archive-2024.zip", 240_000_000L, "zip",
                TransferDirection.OUTGOING, "Pixel 8", false, now - 30 * HOUR));
        a.add(new DemoActivity("a5", "cover-art.png", 3_200_000L, "png",
                TransferDirection.INCOMING, "iPhone 15", true, now - 28 * HOUR));
        a.add(new DemoActivity("a6", "BuildController.java", 12_400L, "java",
                TransferDirection.OUTGOING, "Maya's MacBook Pro", true, now - 26 * HOUR));
        a.add(new DemoActivity("a7", "site-design.fig", 44_000_000L, "fig",
                TransferDirection.INCOMING, "Studio iMac", true, now - 50 * HOUR));
        a.add(new DemoActivity("a8", "invoice-1042.pdf", 220_000L, "pdf",
                TransferDirection.OUTGOING, "ThinkPad X1", true, now - 72 * HOUR));
        a.add(new DemoActivity("a9", "vacation.mov", 980_000_000L, "mov",
                TransferDirection.INCOMING, "iPad Pro", false, now - 76 * HOUR));
        a.add(new DemoActivity("a10", "notes.md", 8_900L, "md",
                TransferDirection.OUTGOING, "Pixel 8", true, now - 96 * HOUR));
        a.add(new DemoActivity("a11", "album.flac", 320_000_000L, "flac",
                TransferDirection.INCOMING, "Maya's MacBook Pro", true, now - 120 * HOUR));
        a.add(new DemoActivity("a12", "config.yaml", 1_400L, "yaml",
                TransferDirection.OUTGOING, "Studio iMac", true, now - 144 * HOUR));
        return a;
    }

    /** A plausible IP for manual "Connect by IP" demo suggestions. */
    public static String exampleIp() {
        return "192.168.1.";
    }
}
