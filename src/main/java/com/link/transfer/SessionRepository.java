package com.link.transfer;

import com.link.domain.Peer;
import com.link.networking.SecureSession;
import com.link.transfer.models.ControlMessage;
import com.link.transfer.models.IncomingBatchState;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class SessionRepository {
    private final Map<UUID, SecureSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, IncomingBatchState> incomingBatches = new ConcurrentHashMap<>();
    private final Map<UUID, OutgoingPendingState> pendingOutgoing = new ConcurrentHashMap<>();
    private final Map<UUID, IncomingPendingState> pendingIncoming = new ConcurrentHashMap<>();
    private final Map<UUID, PauseHandle> pauseHandles = new ConcurrentHashMap<>();

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

    public void clear(UUID transferId) {
        SecureSession session = activeSessions.remove(transferId);
        if (session != null) {
            session.close();
        }
        incomingBatches.remove(transferId);
        PauseHandle handle = pauseHandles.remove(transferId);
        if (handle != null) {
            // Make sure a blocked sender thread is unblocked so it can exit.
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