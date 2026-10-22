package com.link.application;

import com.link.domain.PeerId;
import com.link.domain.TransferSession;
import com.link.domain.TransferState;
import com.link.filesystem.FileStorageService;
import com.link.filesystem.IntegrityService;
import com.link.persistence.TransferRepository;
import com.link.transfer.FileTransferEngine;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates transfer state transitions, UI updates, and engine execution.
 */
public class DefaultTransferCoordinator implements TransferCoordinator {

    private final FileStorageService fileStorageService;
    private final IntegrityService integrityService;
    private final FileTransferEngine fileTransferEngine;
    private final TransferObserver observer;
    private final TransferRepository repository;

    private final Map<UUID, TransferSession> activeSessions = new ConcurrentHashMap<>();

    public DefaultTransferCoordinator(
            FileStorageService fileStorageService,
            IntegrityService integrityService,
            FileTransferEngine fileTransferEngine,
            TransferObserver observer,
            TransferRepository repository) {
        this.fileStorageService = fileStorageService;
        this.integrityService = integrityService;
        this.fileTransferEngine = fileTransferEngine;
        this.observer = observer;
        this.repository = repository;
    }

    @Override
    public UUID requestTransfer(PeerId peerId, List<Path> sourcePaths) {
        UUID transferId = UUID.randomUUID();
        repository.createTransferRecord(transferId.toString(), "OUTGOING", peerId.value(), "OFFERED");
        return transferId;
    }

    @Override
    public void acceptTransfer(UUID transferId, Path destinationDirectory) {
        TransferSession session = activeSessions.get(transferId);
        if (session != null && session.state() == TransferState.OFFERED) {
            repository.updateTransferState(transferId.toString(), "ACCEPTED", 0L);
        }
    }

    @Override
    public void rejectTransfer(UUID transferId) {
        activeSessions.remove(transferId);
        repository.updateTransferState(transferId.toString(), "FAILED", 0L);
    }

    @Override
    public void cancelTransfer(UUID transferId) {
        activeSessions.remove(transferId);
        repository.updateTransferState(transferId.toString(), "CANCELLED", 0L);
    }
}