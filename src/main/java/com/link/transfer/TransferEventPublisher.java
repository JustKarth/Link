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

    // NEW: 8-argument signature for pairing events
    public void publish(UUID transferId, String fileName, TransferDirection direction, 
                       Peer peer, TransferState state, TransferProgress progress, String error, String sasCode) {
        TransferSession snapshot = new TransferSession(transferId, fileName, direction, peer, state, progress, error, sasCode);
        observer.onTransferUpdated(snapshot);
    }

    // ORIGINAL: 7-argument signature forwards to the new one with a null sasCode
    public void publish(UUID transferId, String fileName, TransferDirection direction, 
                       Peer peer, TransferState state, TransferProgress progress, String error) {
        publish(transferId, fileName, direction, peer, state, progress, error, null);
    }

    public void publishThrottled(UUID transferId, String fileName, TransferDirection direction, 
                                Peer peer, TransferState state, TransferProgress progress) {
        long now = System.currentTimeMillis();
        Long lastTime = lastNotificationTime.get(transferId);

        if (lastTime == null || (now - lastTime) >= UI_THROTTLE_MS) {
            lastNotificationTime.put(transferId, now);
            publish(transferId, fileName, direction, peer, state, progress, null, null);
        }
    }

    public void cleanup(UUID transferId) {
        lastNotificationTime.remove(transferId);
    }
}