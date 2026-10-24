package com.link.transfer;

import com.link.networking.SecureSession;
import com.link.transfer.models.IncomingBatchState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionRepository {
    private final Map<UUID, SecureSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, IncomingBatchState> incomingBatches = new ConcurrentHashMap<>();

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
}