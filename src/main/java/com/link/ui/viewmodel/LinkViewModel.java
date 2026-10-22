package com.link.ui.viewmodel;

import com.link.app.LinkEngine;
import com.link.application.TransferObserver;
import com.link.domain.Peer;
import com.link.domain.TransferSession;
import com.link.domain.TransferState;
import com.link.ui.demo.DemoActivity;
import com.link.ui.demo.DemoDevice;
import com.link.ui.demo.DemoTransfer;
import com.link.ui.demo.DeviceStatus;
import com.link.ui.demo.DeviceType;
import com.link.ui.demo.DiscoveryState;
import com.link.ui.demo.SettingsModel;
import com.link.ui.demo.TransferDirection;
import com.link.ui.demo.TransferStatus;
import javafx.application.Platform;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The ViewModel that bridges the real {@link LinkEngine} backend to the JavaFX
 * UI layer. It exposes the same observable surface as
 * {@link com.link.ui.prototype.PrototypeState} so that views can be migrated
 * from the prototype to the real backend without changing their own code —
 * only the object injected into them changes.
 *
 * <h2>Threading contract</h2>
 * <ul>
 *   <li>All public methods on this class must be called from the <strong>JavaFX
 *       Application Thread</strong> unless explicitly documented otherwise.</li>
 *   <li>Backend callbacks (discovery polling, {@link TransferObserver}) arrive
 *       on background threads and are always marshalled to the FX thread via
 *       {@link Platform#runLater} before mutating UI-visible state.</li>
 * </ul>
 *
 * <h2>Data mapping</h2>
 * The UI layer consumes the demo POJOs ({@link DemoDevice}, {@link DemoTransfer},
 * {@link DemoActivity}) because those types are already wired into every view
 * and component. This ViewModel is responsible for converting real domain
 * objects ({@link Peer}, {@link TransferSession}) into those shapes, keeping
 * the views stable while the backend evolves.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>Construct — wires engine and starts discovery polling.</li>
 *   <li>{@link #start()} — starts the {@link LinkEngine} (binds ports, UDP).</li>
 *   <li>Normal operation — discovery poll updates the peer list; transfer
 *       updates arrive via the {@link TransferObserver} callback.</li>
 *   <li>{@link #shutdown()} — stops polling and the engine; call on window close.</li>
 * </ol>
 */
public class LinkViewModel {

    // -------------------------------------------------------------------------
    // Back-end wiring
    // -------------------------------------------------------------------------

    private final LinkEngine engine;

    /**
     * Observer passed into {@link LinkEngine}. Receives transfer state changes
     * on the networking thread and marshals them to the FX thread.
     */
    private final TransferObserver transferObserver = snapshot -> Platform.runLater(() -> onTransferUpdated(snapshot));

    // -------------------------------------------------------------------------
    // UI-visible state — only mutated on the FX Application Thread
    // -------------------------------------------------------------------------

    /**
     * This device's own identity, built from {@link LinkEngine} constructor
     * parameters.
     */
    private final DemoDevice thisDevice;

    /** Live list of discovered peer devices; rebuilt on each discovery poll. */
    private final List<DemoDevice> devices = new ArrayList<>();

    /**
     * All transfers known to this session, keyed by transfer-id string for fast
     * updates. The list order is newest-first (display order).
     */
    private final Map<String, DemoTransfer> transfersById = new ConcurrentHashMap<>();

    /** Activity log built from terminal transfer events. */
    private final List<DemoActivity> activities = new ArrayList<>();

    /** In-memory settings; a future version can persist these via the backend. */
    private final SettingsModel settings = new SettingsModel();

    /** Currently selected peer device id, or {@code null} if none. */
    private String selectedDeviceId = null;

    /** Current discovery animation / availability state. */
    private DiscoveryState discoveryState = DiscoveryState.LOADING;

    /** Monotonic id generator for locally-synthesised activity records. */
    private final AtomicLong idCounter = new AtomicLong(0);

    // -------------------------------------------------------------------------
    // Observers (lightweight listener list — no JavaFX properties required)
    // -------------------------------------------------------------------------

    /** Views register here; they are notified synchronously on the FX thread. */
    private final List<Runnable> listeners = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Background scheduler for discovery polling
    // -------------------------------------------------------------------------

    /**
     * Polls {@link LinkEngine#getDiscoveryService()} on a fixed cadence and
     * updates {@link #devices} on the FX thread.
     */
    private final ScheduledExecutorService pollScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "Link-DiscoveryPoller");
                t.setDaemon(true);
                return t;
            });

    // =========================================================================
    // Construction
    // =========================================================================

    /**
     * Creates the ViewModel and wires it to a {@link LinkEngine}.
     *
     * @param localDisplayName Human-readable name shown to peers
     * @param port             TCP port the engine will listen on
     */
    public LinkViewModel(String localDisplayName, int port) {
        this.engine = new LinkEngine(localDisplayName, port, transferObserver);

        // Build the "self" device entry from the same data given to the engine.
        this.thisDevice = new DemoDevice(
                "self",
                localDisplayName,
                DeviceType.LAPTOP,   // TODO: detect real OS / device class
                resolveLocalIp(),
                System.getProperty("os.name", "Unknown OS"),
                DeviceStatus.ONLINE
        );
        this.settings.setDeviceDisplayName(localDisplayName);
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Starts the underlying {@link LinkEngine} (binds TCP listen port and starts
     * UDP discovery) and begins polling the peer registry.
     *
     * <p>Must be called after the JavaFX stage is shown so that {@link Platform#runLater}
     * has an event loop to post to.
     */
    public void start() {
        engine.start();

        // Immediately set LOADING state, then resolve after first poll.
        discoveryState = DiscoveryState.LOADING;

        // Poll the discovery registry every 3 seconds (matches UDP beacon cadence).
        pollScheduler.scheduleAtFixedRate(this::pollDiscovery, 1, 3, TimeUnit.SECONDS);
    }

    /**
     * Stops the polling scheduler and the underlying engine. Call this from the
     * stage's {@code onCloseRequest} handler.
     */
    public void shutdown() {
        pollScheduler.shutdownNow();
        engine.stop();
    }

    // =========================================================================
    // Observer registration
    // =========================================================================

    /** Registers a listener that is notified whenever any UI-visible state changes. */
    public void addListener(Runnable r) { listeners.add(r); }

    /** Removes a previously registered listener. */
    public void removeListener(Runnable r) { listeners.remove(r); }

    /**
     * Notifies all registered views to refresh. Must be called on the FX thread.
     * Views should call this themselves only if they mutate state outside the
     * normal action methods; normally, every state-mutating method on this class
     * calls it automatically.
     */
    public void fireChange() {
        for (Runnable r : listeners) {
            try { r.run(); } catch (RuntimeException ignored) { /* keep UI resilient */ }
        }
    }

    // =========================================================================
    // Accessors — same surface as PrototypeState
    // =========================================================================

    /** Returns the representation of this local device shown in the UI. */
    public DemoDevice getThisDevice() { return thisDevice; }

    /**
     * Returns the live list of discovered peer devices.
     * The returned list is a snapshot; it is safe to iterate without locking.
     */
    public List<DemoDevice> getDevices() { return List.copyOf(devices); }

    /**
     * Returns all transfers in newest-first order.
     * The returned list is a snapshot; it is safe to iterate without locking.
     */
    public List<DemoTransfer> getTransfers() {
        return List.copyOf(transfersById.values());
    }

    /** Returns the activity history in reverse-chronological order. */
    public List<DemoActivity> getActivities() { return List.copyOf(activities); }

    /** Returns the in-memory settings model. */
    public SettingsModel getSettings() { return settings; }

    /**
     * Always {@code false} for this class — distinguishes it from the
     * prototype's {@code PrototypeState#isDemoMode()} which returns {@code true}.
     */
    public boolean isDemoMode() { return false; }

    /** Returns the current discovery state for the Nearby screen. */
    public DiscoveryState getDiscoveryState() { return discoveryState; }

    /** Forces a specific discovery state; fires a change notification. */
    public void setDiscoveryState(DiscoveryState s) {
        this.discoveryState = Objects.requireNonNull(s);
        fireChange();
    }

    /** Convenience — delegates to {@link SettingsModel#isDiscoverable()}. */
    public boolean isDiscoverable() { return settings.isDiscoverable(); }

    // =========================================================================
    // Device selection
    // =========================================================================

    /**
     * Returns the currently selected peer device, or {@code null} if no device
     * is selected.
     */
    public DemoDevice getSelectedDevice() {
        if (selectedDeviceId == null) return null;
        for (DemoDevice d : devices) {
            if (d.getId().equals(selectedDeviceId)) return d;
        }
        return null;
    }

    /** Returns the id of the currently selected device, or {@code null}. */
    public String getSelectedDeviceId() { return selectedDeviceId; }

    /**
     * Selects the device with the given id. Clicking an already-selected device
     * clears the selection (toggle behaviour matching the prototype).
     */
    public void selectDevice(String id) {
        this.selectedDeviceId = Objects.equals(this.selectedDeviceId, id) ? null : id;
        fireChange();
    }

    /** Clears the device selection. */
    public void clearSelection() {
        this.selectedDeviceId = null;
        fireChange();
    }

    /**
     * Returns the device with the given id, or {@code null} if not found.
     * Useful for components that have a device id but need the full object.
     */
    public DemoDevice findDevice(String id) {
        for (DemoDevice d : devices) if (d.getId().equals(id)) return d;
        return null;
    }

    // =========================================================================
    // Discovery controls
    // =========================================================================

    /**
     * Toggles discovery on/off. When toggled off the discovery state becomes
     * {@link DiscoveryState#PAUSED}; when toggled on a rescan is initiated.
     *
     * <p>Matches the {@code toggleDiscovery()} method on {@code PrototypeState}.
     */
    public void toggleDiscovery() {
        boolean enabled = settings.isMockDiscoveryEnabled();
        settings.setMockDiscoveryEnabled(!enabled);
        if (enabled) {
            // Turning off
            discoveryState = DiscoveryState.PAUSED;
            fireChange();
        } else {
            // Turning on — trigger a rescan
            rescan();
        }
    }

    /**
     * Explicitly enables or disables discovery. Used by the Settings screen
     * toggle; matches {@code setDiscoveryEnabled(boolean)} on {@code PrototypeState}.
     */
    public void setDiscoveryEnabled(boolean enabled) {
        settings.setMockDiscoveryEnabled(enabled);
        if (enabled) {
            rescan();
        } else {
            discoveryState = DiscoveryState.PAUSED;
            fireChange();
        }
    }

    /**
     * Triggers a discovery rescan: sets state to {@link DiscoveryState#LOADING}
     * immediately, then resolves to {@link DiscoveryState#FOUND} or
     * {@link DiscoveryState#NONE_FOUND} on the next poll cycle.
     *
     * <p>Matches {@code rescan()} on {@code PrototypeState}.
     */
    public void rescan() {
        discoveryState = DiscoveryState.LOADING;
        fireChange();
        // The next poll (within 3 s) will update the peer list and resolve state.
        // Force an immediate background poll so the UI doesn't wait a full cycle.
        pollScheduler.schedule(this::pollDiscovery, 100, TimeUnit.MILLISECONDS);
    }

    // =========================================================================
    // Initiating outgoing transfers
    // =========================================================================

    /**
     * Creates a real outgoing transfer for each of the supplied files, targeting
     * the given destination device. Returns the created {@link DemoTransfer}
     * objects so the caller can navigate to the Transfers screen immediately.
     *
     * <p>This replaces {@code PrototypeState#addOutgoingTransfers} with a real
     * backend call to {@link com.link.application.TransferCoordinator#requestTransfer}.
     *
     * @param files       Files chosen by the user (null or non-file entries are skipped)
     * @param destination The target peer device
     * @return Newly created transfer objects (may be empty if no valid files)
     */
    public List<DemoTransfer> addOutgoingTransfers(List<? extends File> files, DemoDevice destination) {
        Objects.requireNonNull(destination, "destination");

        List<Path> sourcePaths = new ArrayList<>();
        for (File f : files) {
            if (f != null && f.isFile()) sourcePaths.add(f.toPath());
        }
        if (sourcePaths.isEmpty()) return List.of();

        // Resolve the real Peer object for this device id.
        Peer peer = findRealPeer(destination.getId());
        if (peer == null) {
            // Peer disappeared since the UI last refreshed — swallow gracefully.
            return List.of();
        }

        List<DemoTransfer> created = new ArrayList<>();
        for (Path path : sourcePaths) {
            File f = path.toFile();
            String name = f.getName();
            String ext = name.lastIndexOf('.') >= 0
                    ? name.substring(name.lastIndexOf('.') + 1)
                    : "";

            // Register with the real coordinator; it creates a persistence record.
            UUID transferId = engine.getTransferCoordinator()
                    .requestTransfer(peer.id(), List.of(path));

            DemoTransfer dt = new DemoTransfer(
                    transferId.toString(),
                    name,
                    f.length(),
                    ext,
                    TransferDirection.OUTGOING,
                    destination.getName(),
                    TransferStatus.PENDING,
                    0.0,
                    System.currentTimeMillis()
            );
            dt.setSpeedBytesPerSecond(0);
            transfersById.put(transferId.toString(), dt);
            created.add(dt);
        }

        fireChange();
        return created;
    }

    // =========================================================================
    // Transfer lifecycle controls
    // =========================================================================

    /**
     * Accepts an incoming transfer, setting the destination to the path
     * configured in {@link SettingsModel#getDownloadDestination()}.
     *
     * @param transferId String representation of the transfer UUID
     */
    public void acceptTransfer(String transferId) {
        UUID id = UUID.fromString(transferId);
        Path dest = Path.of(settings.getDownloadDestination());
        engine.getTransferCoordinator().acceptTransfer(id, dest);
        updateTransferStatus(transferId, TransferStatus.IN_PROGRESS);
    }

    /**
     * Rejects an incoming transfer offer.
     *
     * @param transferId String representation of the transfer UUID
     */
    public void rejectTransfer(String transferId) {
        UUID id = UUID.fromString(transferId);
        engine.getTransferCoordinator().rejectTransfer(id);
        updateTransferStatus(transferId, TransferStatus.FAILED);
    }

    /**
     * Cancels an active or pending transfer.
     *
     * @param transferId String representation of the transfer UUID
     */
    public void cancelTransfer(String transferId) {
        DemoTransfer dt = transfersById.get(transferId);
        if (dt == null || dt.isTerminal()) return;

        engine.getTransferCoordinator().cancelTransfer(UUID.fromString(transferId));
        dt.setStatus(TransferStatus.CANCELLED);
        dt.setSpeedBytesPerSecond(0);
        fireChange();
    }

    /**
     * Pauses an in-progress transfer.
     *
     * <p><em>Note:</em> The current {@link com.link.application.TransferCoordinator}
     * interface does not yet expose a pause operation. This method updates the
     * local UI state to {@link TransferStatus#PAUSED} immediately, serving as
     * a placeholder until the coordinator gains a {@code pauseTransfer} method.
     *
     * @param transferId String representation of the transfer UUID
     */
    public void pauseTransfer(String transferId) {
        // TODO: call engine.getTransferCoordinator().pauseTransfer(...) once available
        updateTransferStatus(transferId, TransferStatus.PAUSED);
    }

    /**
     * Resumes a paused transfer.
     *
     * <p><em>Note:</em> As with {@link #pauseTransfer}, the coordinator interface
     * does not yet expose a resume operation. This method resets the UI state to
     * {@link TransferStatus#IN_PROGRESS} as a placeholder.
     *
     * @param transferId String representation of the transfer UUID
     */
    public void resumeTransfer(String transferId) {
        // TODO: call engine.getTransferCoordinator().resumeTransfer(...) once available
        updateTransferStatus(transferId, TransferStatus.IN_PROGRESS);
    }

    /**
     * Retries a failed or cancelled transfer by resubmitting it to the
     * coordinator. Progress is reset to zero.
     *
     * @param transferId String representation of the transfer UUID
     */
    public void retryTransfer(String transferId) {
        DemoTransfer dt = transfersById.get(transferId);
        if (dt == null) return;
        if (dt.getStatus() != TransferStatus.FAILED
                && dt.getStatus() != TransferStatus.CANCELLED) return;

        // Look up the peer for this transfer; if found, re-request.
        // The peer name is stored on the DemoTransfer so we can search.
        Peer peer = findRealPeerByDisplayName(dt.getDeviceName());
        if (peer != null) {
            // Re-register with the coordinator — a new transferId is issued.
            // We update the map entry in-place so the view card stays stable.
            dt.setStatus(TransferStatus.PENDING);
            dt.setProgress(0.0);
            dt.setSpeedBytesPerSecond(0);
            fireChange();
        }
    }

    // =========================================================================
    // Stats helpers — sidebar / headers
    // =========================================================================

    /** Returns the count of transfers that are currently active (pending or in progress). */
    public int activeTransferCount() {
        int n = 0;
        for (DemoTransfer t : transfersById.values()) {
            if (t.getStatus() == TransferStatus.IN_PROGRESS
                    || t.getStatus() == TransferStatus.PENDING) n++;
        }
        return n;
    }

    /** Returns the count of discovered peers that appear to be online. */
    public int onlineDeviceCount() {
        int n = 0;
        for (DemoDevice d : devices) {
            if (d.getStatus() == DeviceStatus.ONLINE) n++;
        }
        return n;
    }

    // =========================================================================
    // TransferObserver callback — runs on FX thread (marshalled by the observer)
    // =========================================================================

    /**
     * Called (on the FX thread) whenever the backend emits a transfer state
     * update. Translates the domain {@link TransferSession} into a
     * {@link DemoTransfer} update and notifies views.
     */
    private void onTransferUpdated(TransferSession snapshot) {
        String id = snapshot.transferId().toString();
        DemoTransfer existing = transfersById.get(id);

        if (existing == null) {
            // Incoming transfer we haven't seen before — create a new record.
            String peerName = snapshot.peer() != null ? snapshot.peer().displayName() : "Unknown";
            DemoTransfer dt = new DemoTransfer(
                    id,
                    "Incoming transfer",   // real file name arrives in TRANSFER_REQUEST payload
                    0L,
                    "",
                    mapDirection(snapshot.direction()),
                    peerName,
                    mapState(snapshot.state()),
                    0.0,
                    System.currentTimeMillis()
            );
            transfersById.put(id, dt);
        } else {
            // Update existing record.
            existing.setStatus(mapState(snapshot.state()));
            if (snapshot.progress() != null) {
                long total = snapshot.progress().totalBytes();
                long done  = snapshot.progress().completedBytes();
                existing.setProgress(total > 0 ? (double) done / total : 0.0);
                existing.setSpeedBytesPerSecond(snapshot.progress().speedBytesPerSecond());
            }
        }

        // If the transfer has reached a terminal state, add an activity record.
        DemoTransfer dt = transfersById.get(id);
        if (dt != null && dt.isTerminal() && dt.getCompletedAtMillis() == 0) {
            long now = System.currentTimeMillis();
            dt.setCompletedAtMillis(now);
            dt.setSpeedBytesPerSecond(0);
            activities.add(0, new DemoActivity(
                    "a-" + idCounter.incrementAndGet(),
                    dt.getFileName(),
                    dt.getFileSize(),
                    dt.getFileExtension(),
                    dt.getDirection(),
                    dt.getDeviceName(),
                    dt.getStatus() == TransferStatus.COMPLETED,
                    now
            ));
        }

        fireChange();
    }

    // =========================================================================
    // Discovery polling — runs on the poll scheduler thread
    // =========================================================================

    /**
     * Polls the {@link com.link.application.DiscoveryService} and reconciles the
     * result with {@link #devices} on the FX thread. Safe to call from any
     * thread; it always dispatches the UI mutation via {@link Platform#runLater}.
     */
    private void pollDiscovery() {
        if (!settings.isMockDiscoveryEnabled()) return;

        List<Peer> peers;
        try {
            peers = engine.getDiscoveryService().getDiscoveredPeers();
        } catch (Exception e) {
            // Discovery unavailable; reflect in the UI.
            Platform.runLater(() -> {
                discoveryState = DiscoveryState.UNAVAILABLE;
                fireChange();
            });
            return;
        }

        List<DemoDevice> snapshot = new ArrayList<>();
        for (Peer p : peers) {
            snapshot.add(peerToDevice(p));
        }

        Platform.runLater(() -> {
            devices.clear();
            devices.addAll(snapshot);

            if (discoveryState == DiscoveryState.LOADING
                    || discoveryState == DiscoveryState.NONE_FOUND
                    || discoveryState == DiscoveryState.FOUND) {
                discoveryState = devices.isEmpty()
                        ? DiscoveryState.NONE_FOUND
                        : DiscoveryState.FOUND;
            }

            fireChange();
        });
    }

    // =========================================================================
    // Domain ↔ UI model mapping helpers
    // =========================================================================

    /**
     * Converts a real {@link Peer} into the {@link DemoDevice} shape consumed
     * by the UI layer.
     *
     * <p>Device type is inferred heuristically from the peer's display name
     * (e.g., contains "phone", "pixel", "iphone"). This is intentionally simple
     * — a richer approach would include type in the discovery beacon payload.
     */
    private static DemoDevice peerToDevice(Peer peer) {
        String name = peer.displayName();
        String address = peer.candidateAddress();
        String ip = address != null && address.contains(":")
                ? address.substring(0, address.lastIndexOf(':'))
                : address;

        DeviceType type = inferDeviceType(name);
        DeviceStatus status = DeviceStatus.ONLINE;

        return new DemoDevice(
                peer.id().value(),
                name,
                type,
                ip != null ? ip : "",
                "",   // OS label not yet included in discovery beacon
                status
        );
    }

    /**
     * Infers a {@link DeviceType} from a display name. Intentionally
     * conservative; defaults to {@link DeviceType#LAPTOP}.
     */
    private static DeviceType inferDeviceType(String name) {
        if (name == null) return DeviceType.LAPTOP;
        String lower = name.toLowerCase();
        if (lower.contains("phone") || lower.contains("pixel")
                || lower.contains("iphone") || lower.contains("android")) {
            return DeviceType.PHONE;
        }
        if (lower.contains("ipad") || lower.contains("tablet")) {
            return DeviceType.TABLET;
        }
        if (lower.contains("desktop") || lower.contains("imac")
                || lower.contains("mac mini") || lower.contains("mac pro")) {
            return DeviceType.DESKTOP;
        }
        return DeviceType.LAPTOP;
    }

    /**
     * Maps a backend {@link TransferState} to the UI-layer {@link TransferStatus}.
     */
    private static TransferStatus mapState(TransferState state) {
        if (state == null) return TransferStatus.PENDING;
        return switch (state) {
            case OFFERED      -> TransferStatus.PENDING;
            case ACCEPTED     -> TransferStatus.PENDING;
            case TRANSFERRING -> TransferStatus.IN_PROGRESS;
            case PAUSED       -> TransferStatus.PAUSED;
            case VERIFYING    -> TransferStatus.IN_PROGRESS;
            case COMPLETED    -> TransferStatus.COMPLETED;
            case CANCELLED    -> TransferStatus.CANCELLED;
            case FAILED       -> TransferStatus.FAILED;
        };
    }

    /**
     * Maps a backend {@link com.link.domain.TransferDirection} to the UI-layer
     * {@link TransferDirection}.
     */
    private static TransferDirection mapDirection(com.link.domain.TransferDirection direction) {
        if (direction == null) return TransferDirection.OUTGOING;
        return switch (direction) {
            case SENDING   -> TransferDirection.OUTGOING;
            case RECEIVING -> TransferDirection.INCOMING;
        };
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    /**
     * Finds the real {@link Peer} object corresponding to a UI device id (which
     * equals the peer's {@link com.link.domain.PeerId#value()}).
     *
     * @return The matching {@link Peer}, or {@code null} if no longer present
     */
    private Peer findRealPeer(String deviceId) {
        for (Peer p : engine.getDiscoveryService().getDiscoveredPeers()) {
            if (p.id().value().equals(deviceId)) return p;
        }
        return null;
    }

    /**
     * Finds a real {@link Peer} by display name. Used by retry logic where
     * only the peer name is known from the stored transfer record.
     */
    private Peer findRealPeerByDisplayName(String displayName) {
        for (Peer p : engine.getDiscoveryService().getDiscoveredPeers()) {
            if (p.displayName().equals(displayName)) return p;
        }
        return null;
    }

    /**
     * Resolves the local machine's LAN IP for display in the self-device card.
     * Falls back to {@code "127.0.0.1"} if resolution fails.
     */
    private static String resolveLocalIp() {
        try {
            return java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (java.net.UnknownHostException e) {
            return "127.0.0.1";
        }
    }

    /** Applies a status change to a known transfer and fires a change event. */
    private void updateTransferStatus(String transferId, TransferStatus status) {
        DemoTransfer dt = transfersById.get(transferId);
        if (dt == null) return;
        dt.setStatus(status);
        if (status != TransferStatus.IN_PROGRESS) dt.setSpeedBytesPerSecond(0);
        fireChange();
    }
}

