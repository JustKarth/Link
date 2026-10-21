package com.link.application;

import com.link.domain.PeerId;
import com.link.domain.TransferSession;
import com.link.domain.TransferState;
import com.link.filesystem.FileStorageService;
import com.link.filesystem.IntegrityService;
import com.link.transfer.FileTransferEngine;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class DefaultTransferCoordinator implements TransferCoordinator {

    private final FileStorageService fileStorageService;
    private final IntegrityService integrityService;
    private final FileTransferEngine fileTransferEngine;
    private final TransferObserver observer;
    
    private final Map<UUID, TransferSession> activeSessions = new ConcurrentHashMap<>();

    public DefaultTransferCoordinator(
            FileStorageService fileStorageService,
            IntegrityService integrityService,
            FileTransferEngine fileTransferEngine,
            TransferObserver observer) {
        this.fileStorageService = fileStorageService;
        this.integrityService = integrityService;
        this.fileTransferEngine = fileTransferEngine;
        this.observer = observer;
    }

    @Override
    public UUID requestTransfer(PeerId peerId, List<Path> sourcePaths) {
        UUID transferId = UUID.randomUUID();
        return transferId;
    }

    @Override
    public void acceptTransfer(UUID transferId, Path destinationDirectory) {
        TransferSession session = activeSessions.get(transferId);
        if (session != null && session.state() == TransferState.OFFERED) {
        }
    }

    @Override
    public void rejectTransfer(UUID transferId) {
        activeSessions.remove(transferId);
    }

    @Override
    public void cancelTransfer(UUID transferId) {
        activeSessions.remove(transferId);
    }
}