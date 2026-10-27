package com.link.application;

import com.link.domain.PeerId;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public interface TransferCoordinator {
    UUID requestTransfer(PeerId targetPeerId, List<Path> sourcePaths);
    void acceptTransfer(UUID transferId, Path downloadDirectory);
    void rejectTransfer(UUID transferId);
    void cancelTransfer(UUID transferId);
    void confirmPairing(UUID transferId, boolean accepted);
}