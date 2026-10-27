package com.link.application;

import com.link.domain.PeerId;
import com.link.persistence.TransferRepository;
import com.link.transfer.FileTransferEngine;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class DefaultTransferCoordinator implements TransferCoordinator {

    private final FileTransferEngine fileTransferEngine;
    private final TransferRepository repository;

    public DefaultTransferCoordinator(
            FileTransferEngine fileTransferEngine,
            TransferRepository repository) {
        this.fileTransferEngine = fileTransferEngine;
        this.repository = repository;
    }

    @Override
    public UUID requestTransfer(PeerId peerId, List<Path> sourcePaths) {
        UUID transferId = UUID.randomUUID();
        
        repository.createTransferRecord(transferId.toString(), "SENDING", peerId.value(), "OFFERED");
        fileTransferEngine.initiateTransfer(transferId, peerId, sourcePaths);
        
        return transferId;
    }

    @Override
    public void acceptTransfer(UUID transferId, Path destinationDirectory) {
        repository.updateTransferState(transferId.toString(), "ACCEPTED", 0L);
        fileTransferEngine.acceptTransfer(transferId, destinationDirectory);
    }

    @Override
    public void confirmPairing(UUID transferId, boolean accepted) {
        fileTransferEngine.confirmPairing(transferId, accepted);
    }

    @Override
    public void rejectTransfer(UUID transferId) {
        repository.updateTransferState(transferId.toString(), "FAILED", 0L);
        fileTransferEngine.rejectTransfer(transferId);
    }

    @Override
    public void cancelTransfer(UUID transferId) {
        repository.updateTransferState(transferId.toString(), "CANCELLED", 0L);
        fileTransferEngine.cancelTransfer(transferId);
    }
}