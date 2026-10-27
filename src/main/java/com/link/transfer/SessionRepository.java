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

public class SessionRepository {
    private final Map<UUID, SecureSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, IncomingBatchState> incomingBatches = new ConcurrentHashMap<>();
    private final Map<UUID, OutgoingPendingState> pendingOutgoing = new ConcurrentHashMap<>();
    private final Map<UUID, IncomingPendingState> pendingIncoming = new ConcurrentHashMap<>();

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

    public record IncomingPendingState(ControlMessage request, SecureSession session, String fingerprint) {}
    public record OutgoingPendingState(Peer targetPeer, SecureSession session, List<Path> sourcePaths, String fingerprint) {}
}