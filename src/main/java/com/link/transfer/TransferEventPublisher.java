package com.link.transfer;

import com.link.application.TransferObserver;
import com.link.domain.Peer;
import com.link.domain.TransferDirection;
import com.link.domain.TransferProgress;
import com.link.domain.TransferSession;
import com.link.domain.TransferState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TransferEventPublisher {
    private static final long UI_THROTTLE_MS = 100;
    
    private final TransferObserver observer;
    private final Map<UUID, Long> lastNotificationTime = new ConcurrentHashMap<>();

    public TransferEventPublisher(TransferObserver observer) {
        this.observer = observer;
    }

    public void publish(UUID transferId, String fileName, TransferDirection direction, 
                       Peer peer, TransferState state, TransferProgress progress, String error) {
        TransferSession snapshot = new TransferSession(transferId, fileName, direction, peer, state, progress, error);
        observer.onTransferUpdated(snapshot);
    }

    public void publishThrottled(UUID transferId, String fileName, TransferDirection direction, 
                                Peer peer, TransferState state, TransferProgress progress) {
        long now = System.currentTimeMillis();
        Long lastTime = lastNotificationTime.get(transferId);

        if (lastTime == null || (now - lastTime) >= UI_THROTTLE_MS) {
            lastNotificationTime.put(transferId, now);
            publish(transferId, fileName, direction, peer, state, progress, null);
        }
    }

    public void cleanup(UUID transferId) {
        lastNotificationTime.remove(transferId);
    }
}