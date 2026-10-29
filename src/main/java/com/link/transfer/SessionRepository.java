package com.link.transfer;

import com.link.domain.Peer;
import com.link.domain.TransferDirection;
import com.link.networking.SecureSession;
import com.link.transfer.models.ControlMessage;
import com.link.transfer.models.IncomingBatchState;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public class SessionRepository {
    private final Map<UUID, SecureSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, IncomingBatchState> incomingBatches = new ConcurrentHashMap<>();
    private final Map<UUID, PairingPendingState> pendingPairings = new ConcurrentHashMap<>();
    private final Map<UUID, OutgoingPendingState> pendingOutgoing = new ConcurrentHashMap<>();
    private final Map<UUID, IncomingPendingState> pendingIncoming = new ConcurrentHashMap<>();
    private final Map<UUID, PauseHandle> pauseHandles = new ConcurrentHashMap<>();

    /**
     * Unified pending state for an active pairing session (either initiating or receiving).
     */
    public static final class PairingPendingState {
        private final UUID transferId;
        private final SecureSession session;
        private final Peer peer;
        private final String fingerprint;
        private final TransferDirection direction;
        private final List<Path> sourcePaths;
        private final String summaryName;
        private final CompletableFuture<Boolean> localDecision = new CompletableFuture<>();
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private volatile ScheduledFuture<?> timeoutFuture;

        public PairingPendingState(
                UUID transferId,
                SecureSession session,
                Peer peer,
                String fingerprint,
                TransferDirection direction,
                List<Path> sourcePaths,
                String summaryName) {
            this.transferId = transferId;
            this.session = session;
            this.peer = peer;
            this.fingerprint = fingerprint;
            this.direction = direction;
            this.sourcePaths = sourcePaths;
            this.summaryName = summaryName;
        }

        public UUID transferId() { return transferId; }
        public SecureSession session() { return session; }
        public Peer peer() { return peer; }
        public String fingerprint() { return fingerprint; }
        public TransferDirection direction() { return direction; }
        public List<Path> sourcePaths() { return sourcePaths; }
        public String summaryName() { return summaryName; }
        public CompletableFuture<Boolean> localDecision() { return localDecision; }

        public void setTimeoutFuture(ScheduledFuture<?> future) {
            this.timeoutFuture = future;
        }

        public void completeSuccess() {
            if (completed.compareAndSet(false, true)) {
                if (timeoutFuture != null) {
                    timeoutFuture.cancel(false);
                }
            }
        }

        public void cancel() {
            if (completed.compareAndSet(false, true)) {
                if (timeoutFuture != null) {
                    timeoutFuture.cancel(false);
                }
                localDecision.complete(false);
                try {
                    session.close();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Coordinates pause/resume for an active sender-side transfer.
     * The sender thread checks {@link #isPaused()} between chunks and calls
     * {@link #awaitResume()} to block until the UI calls resume.
     */
    public static final class PauseHandle {
        private final AtomicBoolean paused = new AtomicBoolean(false);
        private volatile CountDownLatch resumeLatch = new CountDownLatch(0);

        /** Returns true while the transfer is in a paused state. */
        public boolean isPaused() { return paused.get(); }

        /**
         * Signals the sender thread to pause after the current chunk.
         * No-op if already paused.
         */
        public void pause() {
            if (paused.compareAndSet(false, true)) {
                resumeLatch = new CountDownLatch(1);
            }
        }

        /**
         * Releases the sender thread. No-op if not paused.
         */
        public void resume() {
            if (paused.compareAndSet(true, false)) {
                resumeLatch.countDown();
            }
        }

        /**
         * Called by the sender thread between chunks. Blocks until
         * {@link #resume()} is called if currently paused.
         * @throws InterruptedException if the thread is interrupted while waiting
         */
        public void awaitResume() throws InterruptedException {
            resumeLatch.await();
        }
    }

    public void addSession(UUID transferId, SecureSession session) {
        activeSessions.put(transferId, session);
    }

    public SecureSession getSession(UUID transferId) {
        return activeSessions.get(transferId);
    }

    public void addBatch(UUID transferId, IncomingBatchState batch) {
        incomingBatches.put(transferId, batch);
    }

    public IncomingBatchState getBatch(UUID transferId) {
        return incomingBatches.get(transferId);
    }

    public void addPendingPairing(UUID transferId, PairingPendingState state) {
        pendingPairings.put(transferId, state);
    }

    public PairingPendingState getPendingPairing(UUID transferId) {
        return pendingPairings.get(transferId);
    }

    public PairingPendingState removePendingPairing(UUID transferId) {
        return pendingPairings.remove(transferId);
    }

    public void clear(UUID transferId) {
        SecureSession session = activeSessions.remove(transferId);
        if (session != null) {
            session.close();
        }
        incomingBatches.remove(transferId);
        PairingPendingState pairing = pendingPairings.remove(transferId);
        if (pairing != null) {
            pairing.cancel();
        }
        pendingOutgoing.remove(transferId);
        pendingIncoming.remove(transferId);
        PauseHandle handle = pauseHandles.remove(transferId);
        if (handle != null) {
            handle.resume();
        }
    }

    public void addPendingIncoming(UUID transferId, IncomingPendingState state) {
        pendingIncoming.put(transferId, state);
    }

    public IncomingPendingState getAndRemovePendingIncoming(UUID transferId) {
        return pendingIncoming.remove(transferId);
    }

    public void addPendingOutgoing(UUID transferId, OutgoingPendingState state) {
        pendingOutgoing.put(transferId, state);
    }

    public OutgoingPendingState getAndRemovePendingOutgoing(UUID transferId) {
        return pendingOutgoing.remove(transferId);
    }

    // ---- Pause/resume handles -------------------------------------------

    /** Creates and registers a fresh PauseHandle for an outgoing transfer. */
    public PauseHandle createPauseHandle(UUID transferId) {
        PauseHandle handle = new PauseHandle();
        pauseHandles.put(transferId, handle);
        return handle;
    }

    /** Returns the PauseHandle for a transfer, or {@code null} if none registered. */
    public PauseHandle getPauseHandle(UUID transferId) {
        return pauseHandles.get(transferId);
    }

    public record IncomingPendingState(ControlMessage request, SecureSession session, String fingerprint) {}
    public record OutgoingPendingState(Peer targetPeer, SecureSession session, List<Path> sourcePaths, String fingerprint) {}
}
