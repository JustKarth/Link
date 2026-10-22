package com.link.ui.prototype;

import com.link.ui.demo.DemoActivity;
import com.link.ui.demo.DemoDataFactory;
import com.link.ui.demo.DemoDevice;
import com.link.ui.demo.DemoTransfer;
import com.link.ui.demo.DiscoveryState;
import com.link.ui.demo.SettingsModel;
import com.link.ui.demo.TransferDirection;
import com.link.ui.demo.TransferStatus;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Central, in-memory state for the prototype. It is the single seam between
 * the demo layer and the UI: every view reads from here, and every user action
 * mutates here. A future backend can replace the contents of this class (or
 * implement the same surface against real services) without touching views.
 *
 * <p>State is plain Java (no JavaFX properties) so the demo POJOs stay clean;
 * the only JavaFX touch here is the {@link Timeline} that drives the simulated
 * transfer progress, which runs on the JavaFX Application Thread and is safe.
 */
public class PrototypeState {

    private final DemoDevice thisDevice;
    private final List<DemoDevice> devices = new ArrayList<>();
    private final List<DemoTransfer> transfers = new ArrayList<>();
    private final List<DemoActivity> activities = new ArrayList<>();
    private final SettingsModel settings = new SettingsModel();

    private DiscoveryState discoveryState = DiscoveryState.LOADING;
    private String selectedDeviceId = null;

    // Transfer simulation
    private final Timeline transferEngine;
    private long lastTickMillis = 0L;
    private final AtomicLong idCounter = new AtomicLong(1000);

    // Lightweight observer list — views register their refresh() here.
    private final List<Runnable> listeners = new ArrayList<>();

    public PrototypeState() {
        long now = System.currentTimeMillis();
        this.thisDevice = DemoDataFactory.thisDevice();
        this.settings.setDeviceDisplayName(thisDevice.getName());
        this.devices.addAll(DemoDataFactory.nearbyDevices());
        this.transfers.addAll(DemoDataFactory.seedTransfers(now));
        this.activities.addAll(DemoDataFactory.seedActivities(now));
        this.transferEngine = buildEngine();

        // Brief loading, then discovery reports devices found.
        Timeline startup = new Timeline(new javafx.animation.KeyFrame(Duration.millis(1400), e -> {
            discoveryState = DiscoveryState.FOUND;
            fireChange();
        }));
        startup.setCycleCount(1);
        startup.play();
    }

    // ---- Observers --------------------------------------------------------

    public void addListener(Runnable r) { listeners.add(r); }
    public void removeListener(Runnable r) { listeners.remove(r); }

    /** Notify all registered views to refresh. Must be called on the FX thread. */
    public void fireChange() {
        for (Runnable r : listeners) {
            try { r.run(); } catch (RuntimeException ignored) { /* keep UI resilient */ }
        }
    }

    // ---- Accessors --------------------------------------------------------

    public DemoDevice getThisDevice() { return thisDevice; }
    public List<DemoDevice> getDevices() { return devices; }
    public List<DemoTransfer> getTransfers() { return transfers; }
    public List<DemoActivity> getActivities() { return activities; }
    public SettingsModel getSettings() { return settings; }

    public boolean isDemoMode() { return true; }

    public DiscoveryState getDiscoveryState() { return discoveryState; }
    public void setDiscoveryState(DiscoveryState s) {
        this.discoveryState = Objects.requireNonNull(s);
        fireChange();
    }

    public boolean isDiscoverable() { return settings.isDiscoverable(); }

    // ---- Device selection -------------------------------------------------

    public DemoDevice getSelectedDevice() {
        if (selectedDeviceId == null) return null;
        for (DemoDevice d : devices) if (d.getId().equals(selectedDeviceId)) return d;
        return null;
    }

    public String getSelectedDeviceId() { return selectedDeviceId; }

    public void selectDevice(String id) {
        // Toggle: clicking the already-selected device clears the selection.
        this.selectedDeviceId = Objects.equals(this.selectedDeviceId, id) ? null : id;
        fireChange();
    }

    public void clearSelection() {
        this.selectedDeviceId = null;
        fireChange();
    }

    public DemoDevice findDevice(String id) {
        for (DemoDevice d : devices) if (d.getId().equals(id)) return d;
        return null;
    }

    // ---- Discovery controls (demo only) ----------------------------------

    public void toggleDiscovery() {
        boolean enabled = settings.isMockDiscoveryEnabled();
        settings.setMockDiscoveryEnabled(!enabled);
        if (enabled) {
            discoveryState = DiscoveryState.PAUSED;
        } else {
            discoveryState = devices.isEmpty() ? DiscoveryState.NONE_FOUND : DiscoveryState.FOUND;
        }
        fireChange();
    }

    /** Explicit enable/disable of mock discovery, used by the Settings toggle. */
    public void setDiscoveryEnabled(boolean enabled) {
        settings.setMockDiscoveryEnabled(enabled);
        if (enabled) {
            rescan();
        } else {
            discoveryState = DiscoveryState.PAUSED;
            fireChange();
        }
    }

    /** Used by the Nearby screen's discovery toggle / retry affordance. */
    public void rescan() {
        discoveryState = DiscoveryState.LOADING;
        fireChange();
        Timeline t = new Timeline(new javafx.animation.KeyFrame(Duration.millis(1300), e -> {
            discoveryState = devices.isEmpty() ? DiscoveryState.NONE_FOUND : DiscoveryState.FOUND;
            fireChange();
        }));
        t.setCycleCount(1);
        t.play();
    }

    // ---- Sending files (simulated) ---------------------------------------

    /**
     * Create one demo transfer per selected file, queued as PENDING and then
     * started by the transfer engine. Returns the created transfers so the
     * caller can navigate to the Transfers screen.
     */
    public List<DemoTransfer> addOutgoingTransfers(List<? extends java.io.File> files, DemoDevice destination) {
        Objects.requireNonNull(destination, "destination");
        List<DemoTransfer> created = new ArrayList<>();
        for (java.io.File f : files) {
            if (f == null || !f.isFile()) continue;
            String name = f.getName();
            String ext = name.substring(name.lastIndexOf('.') + 1);
            if (name.lastIndexOf('.') < 0) ext = "";
            long size = f.length();
            long totalSec = ThreadLocalRandom.current().nextLong(6, 14);
            long speed = totalSec <= 0 ? size : size / totalSec;
            DemoTransfer t = new DemoTransfer(
                    "t-" + idCounter.incrementAndGet() + "-" + UUID.randomUUID(),
                    name, size, ext,
                    TransferDirection.OUTGOING, destination.getName(),
                    TransferStatus.PENDING, 0.0, System.currentTimeMillis());
            t.setSpeedBytesPerSecond(Math.max(1, speed));
            transfers.add(0, t);
            created.add(t);
        }
        fireChange();
        return created;
    }

    // ---- Demo transfer controls (no real backend) -----------------------

    public void startPendingTransfers() {
        for (DemoTransfer t : transfers) {
            if (t.getStatus() == TransferStatus.PENDING) {
                t.setStatus(TransferStatus.IN_PROGRESS);
            }
        }
        fireChange();
    }

    public void pauseTransfer(String id) { mutateTransfer(id, TransferStatus.PAUSED); }
    public void resumeTransfer(String id) { mutateTransfer(id, TransferStatus.IN_PROGRESS); }

    public void cancelTransfer(String id) {
        for (DemoTransfer t : transfers) {
            if (t.getId().equals(id) && !t.isTerminal()) {
                t.setStatus(TransferStatus.CANCELLED);
                t.setSpeedBytesPerSecond(0);
            }
        }
        fireChange();
    }

    public void retryTransfer(String id) {
        for (DemoTransfer t : transfers) {
            if (t.getId().equals(id)
                    && (t.getStatus() == TransferStatus.FAILED || t.getStatus() == TransferStatus.CANCELLED)) {
                t.setStatus(TransferStatus.IN_PROGRESS);
                t.setProgress(0.0);
                t.setSpeedBytesPerSecond(Math.max(1, t.getFileSize()
                        / ThreadLocalRandom.current().nextLong(6, 14)));
            }
        }
        fireChange();
    }

    private void mutateTransfer(String id, TransferStatus status) {
        for (DemoTransfer t : transfers) {
            if (t.getId().equals(id)) {
                t.setStatus(status);
                if (status != TransferStatus.IN_PROGRESS) t.setSpeedBytesPerSecond(0);
            }
        }
        fireChange();
    }

    // ---- Transfer simulation engine --------------------------------------

    private Timeline buildEngine() {
        Timeline tl = new Timeline(new javafx.animation.KeyFrame(Duration.millis(400), e -> tick()));
        tl.setCycleCount(Timeline.INDEFINITE);
        return tl;
    }

    public void startEngine() {
        lastTickMillis = System.currentTimeMillis();
        transferEngine.play();
    }

    public void stopEngine() {
        transferEngine.stop();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        double dt = Math.min(2.0, (now - lastTickMillis) / 1000.0);
        lastTickMillis = now;
        if (dt <= 0) return;

        Deque<DemoActivity> completedNow = new ArrayDeque<>();

        for (DemoTransfer t : transfers) {
            if (t.getStatus() != TransferStatus.IN_PROGRESS) continue;
            // Advance progress by the fraction implied by the simulated speed.
            double inc = t.getSpeedBytesPerSecond() * dt / Math.max(1, t.getFileSize());
            double p = t.getProgress() + inc;
            // Light jitter on displayed speed for realism.
            long jitter = (long) (t.getSpeedBytesPerSecond() * ThreadLocalRandom.current().nextDouble(-0.08, 0.08));
            t.setSpeedBytesPerSecond(Math.max(1, t.getSpeedBytesPerSecond() + jitter));

            if (p >= 1.0) {
                t.setProgress(1.0);
                t.setStatus(TransferStatus.COMPLETED);
                t.setCompletedAtMillis(now);
                t.setSpeedBytesPerSecond(0);
                completedNow.addFirst(new DemoActivity(
                        "a-" + idCounter.incrementAndGet(),
                        t.getFileName(), t.getFileSize(), t.getFileExtension(),
                        t.getDirection(), t.getDeviceName(), true, now));
            } else {
                t.setProgress(p);
            }
        }

        if (!completedNow.isEmpty()) {
            activities.addAll(0, completedNow);
            // A completion (and the new activity it produces) is a structural
            // change worth a full refresh. Pure progress advancement is not —
            // the Transfers view polls progress itself for smooth bars.
            fireChange();
        }
    }

    // ---- Stats helpers (for the sidebar & headers) -----------------------

    public int activeTransferCount() {
        int n = 0;
        for (DemoTransfer t : transfers) {
            if (t.getStatus() == TransferStatus.IN_PROGRESS || t.getStatus() == TransferStatus.PENDING) n++;
        }
        return n;
    }

    public int onlineDeviceCount() {
        int n = 0;
        for (DemoDevice d : devices) if (d.getStatus() == com.link.ui.demo.DeviceStatus.ONLINE) n++;
        return n;
    }
}
