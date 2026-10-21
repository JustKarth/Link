package com.link.application;

import com.link.domain.PeerId;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public interface TransferCoordinator {
    UUID requestTransfer(PeerId peerId, List<Path> sourcePaths); 
    void acceptTransfer(UUID transferId, Path destinationDirectory);
    void rejectTransfer(UUID transferId);
    void cancelTransfer(UUID transferId);
}