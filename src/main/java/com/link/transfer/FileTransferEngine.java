package com.link.transfer;

import com.link.application.DiscoveryService;
import com.link.domain.Peer;
import com.link.domain.PeerId;
import com.link.domain.TransferDirection;
import com.link.domain.TransferProgress;
import com.link.domain.TransferState;
import com.link.networking.ConnectionManager;
import com.link.networking.SecureSession;
import com.link.transfer.models.ControlMessage;
import com.link.transfer.models.FileInfo;
import com.link.transfer.models.IncomingBatchState;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileTransferEngine {

    private final String localPeerId;
    private final String localDisplayName;
    
    private final DiscoveryService discoveryService;
    private final ConnectionManager connectionManager;
    private final StreamWorker streamWorker;
    private final TransferProtocolManager protocolManager;
    private final SessionRepository repository;
    private final TransferEventPublisher publisher;
    
    private final ExecutorService transferPool = Executors.newCachedThreadPool();

    public FileTransferEngine(
            String localPeerId, String localDisplayName,
            DiscoveryService discoveryService, ConnectionManager connectionManager, 
            StreamWorker streamWorker, TransferProtocolManager protocolManager,
            SessionRepository repository, TransferEventPublisher publisher) {
        
        this.localPeerId = localPeerId;
        this.localDisplayName = localDisplayName;
        this.discoveryService = discoveryService;
        this.connectionManager = connectionManager;
        this.streamWorker = streamWorker;
        this.protocolManager = protocolManager;
        this.repository = repository;
        this.publisher = publisher;
    }

    // RESTORED: Matches the original method signature expected by DefaultTransferCoordinator
    public void initiateTransfer(UUID transferId, PeerId peerId, List<Path> sourcePaths) {
        transferPool.submit(() -> {
            String summaryName = sourcePaths.size() == 1 ? sourcePaths.get(0).getFileName().toString() : sourcePaths.size() + " files";
            Peer peerForCatch = null;

            try {
                // 1. Declare as final so it can be safely used inside the lambda
                final Peer targetPeer = discoveryService.getDiscoveredPeers().stream()
                        .filter(p -> p.id().equals(peerId)).findFirst()
                        .orElseThrow(() -> new RuntimeException("Peer not found"));
                
                peerForCatch = targetPeer;

                publisher.publish(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.VERIFYING, new TransferProgress(0, 0, 0), null);

                // Build manifest internally so caller doesn't have to
                List<FileInfo> fileManifest = streamWorker.createFileManifest(sourcePaths);
                if (fileManifest.isEmpty()) throw new IllegalArgumentException("No valid files selected");
                long totalBatchBytes = fileManifest.stream().mapToLong(FileInfo::fileSize).sum();

                SecureSession session = connectionManager.connect(targetPeer);
                repository.addSession(transferId, session);
                
                ControlMessage request = new ControlMessage("TRANSFER_REQUEST", transferId.toString(), summaryName, totalBatchBytes, fileManifest, localPeerId, localDisplayName, null, 0);
                protocolManager.sendMessage(session, request);

                ControlMessage response = protocolManager.receiveMessage(session);

                if ("TRANSFER_ACCEPTED".equals(response.type())) {
                    long resumeOffset = response.offset();
                    publisher.publish(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.TRANSFERRING, new TransferProgress(totalBatchBytes, resumeOffset, 0), null);

                    long startTime = System.currentTimeMillis();
                    
                    // 2. targetPeer is now final, so this lambda compiles perfectly
                    streamWorker.sendFiles(session, sourcePaths, resumeOffset, (bytesSent) -> {
                        long speed = calculateSpeed(startTime, bytesSent);
                        publisher.publishThrottled(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.TRANSFERRING, new TransferProgress(totalBatchBytes, bytesSent, speed));
                    });

                    protocolManager.sendMessage(session, new ControlMessage("TRANSFER_COMPLETED", transferId.toString(), null, 0, null, null, null, null, 0));
                    publisher.publish(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.COMPLETED, new TransferProgress(totalBatchBytes, totalBatchBytes, 0), null);
                    
                } else if ("TRANSFER_REJECTED".equals(response.type())) {
                    publisher.publish(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.FAILED, new TransferProgress(totalBatchBytes, 0, 0), "Peer rejected transfer");
                }
            } catch (Exception e) {
                // Use the fallback reference here in case it failed before the peer was found
                publisher.publish(transferId, summaryName, TransferDirection.SENDING, peerForCatch, TransferState.FAILED, new TransferProgress(0, 0, 0), e.getMessage());
            } finally {
                cleanupSession(transferId);
            }
        });
    }

    public void handleIncomingSession(SecureSession session) {
        transferPool.submit(() -> {
            try {
                ControlMessage request = protocolManager.receiveMessage(session);

                if ("TRANSFER_REQUEST".equals(request.type())) {
                    UUID transferId = UUID.fromString(request.transferId());
                    repository.addSession(transferId, session);
                    
                    Peer senderPeer = new Peer(new PeerId(request.senderId()), request.senderDisplayName(), "", false);
                    List<FileInfo> files = request.files() != null ? request.files() : List.of(new FileInfo(request.fileName(), request.fileSize(), request.checksum()));
                    long totalSize = files.stream().mapToLong(FileInfo::fileSize).sum();

                    repository.addBatch(transferId, new IncomingBatchState(files, totalSize, senderPeer, null));
                    publisher.publish(transferId, request.fileName(), TransferDirection.RECEIVING, senderPeer, TransferState.OFFERED, new TransferProgress(totalSize, 0, 0), null);
                }
            } catch (Exception e) {
                session.close();
            }
        });
    }

    public void acceptTransfer(UUID transferId, Path destinationDirectory) {
        transferPool.submit(() -> {
            SecureSession session = repository.getSession(transferId);
            IncomingBatchState batch = repository.getBatch(transferId);
            if (session == null || batch == null) return;

            repository.addBatch(transferId, new IncomingBatchState(batch.files(), batch.totalBatchSize(), batch.peer(), destinationDirectory));
            String summaryName = batch.files().size() == 1 ? batch.files().get(0).fileName() : batch.files().size() + " files";

            try {
                long existingOffset = calculateExistingOffset(batch.files(), destinationDirectory);
                
                protocolManager.sendMessage(session, new ControlMessage("TRANSFER_ACCEPTED", transferId.toString(), null, 0, null, null, null, null, existingOffset));
                publisher.publish(transferId, summaryName, TransferDirection.RECEIVING, batch.peer(), TransferState.TRANSFERRING, new TransferProgress(batch.totalBatchSize(), existingOffset, 0), null);

                long startTime = System.currentTimeMillis();
                
                streamWorker.receiveFiles(session, batch.files(), destinationDirectory, existingOffset, (bytesReceived) -> {
                    long speed = calculateSpeed(startTime, bytesReceived);
                    publisher.publishThrottled(transferId, summaryName, TransferDirection.RECEIVING, batch.peer(), TransferState.TRANSFERRING, new TransferProgress(batch.totalBatchSize(), bytesReceived, speed));
                });
                
                publisher.publish(transferId, summaryName, TransferDirection.RECEIVING, batch.peer(), TransferState.VERIFYING, new TransferProgress(batch.totalBatchSize(), batch.totalBatchSize(), 0), null);

                if (streamWorker.verifyAndCommit(batch.files(), destinationDirectory)) {
                    publisher.publish(transferId, summaryName, TransferDirection.RECEIVING, batch.peer(), TransferState.COMPLETED, new TransferProgress(batch.totalBatchSize(), batch.totalBatchSize(), 0), null);
                } else {
                    streamWorker.cleanupTempFiles(batch.files(), destinationDirectory);
                    publisher.publish(transferId, summaryName, TransferDirection.RECEIVING, batch.peer(), TransferState.FAILED, new TransferProgress(batch.totalBatchSize(), batch.totalBatchSize(), 0), "Checksum mismatch");
                }
            } catch (Exception e) {
                publisher.publish(transferId, summaryName, TransferDirection.RECEIVING, batch.peer(), TransferState.FAILED, new TransferProgress(batch.totalBatchSize(), 0, 0), e.getMessage());
            } finally {
                cleanupSession(transferId);
            }
        });
    }

    // RESTORED: Missing rejectTransfer method
    public void rejectTransfer(UUID transferId) {
        transferPool.submit(() -> {
            SecureSession session = repository.getSession(transferId);
            IncomingBatchState batch = repository.getBatch(transferId);
            String summaryName = batch != null ? (batch.files().size() == 1 ? batch.files().get(0).fileName() : batch.files().size() + " files") : "Unknown";
            Peer peer = batch != null ? batch.peer() : null;

            if (session != null) {
                try {
                    protocolManager.sendMessage(session, new ControlMessage("TRANSFER_REJECTED", transferId.toString(), null, 0, null, null, null, null, 0));
                    if (batch != null && batch.destDir() != null) {
                        streamWorker.cleanupTempFiles(batch.files(), batch.destDir());
                    }
                    publisher.publish(transferId, summaryName, TransferDirection.RECEIVING, peer, TransferState.CANCELLED, null, "Transfer rejected by local user");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    cleanupSession(transferId);
                }
            }
        });
    }

    public void cancelTransfer(UUID transferId) {
        IncomingBatchState batch = repository.getBatch(transferId);
        if (batch != null) {
            streamWorker.cleanupTempFiles(batch.files(), batch.destDir());
            String summaryName = batch.files().size() == 1 ? batch.files().get(0).fileName() : batch.files().size() + " files";
            publisher.publish(transferId, summaryName, TransferDirection.SENDING, batch.peer(), TransferState.CANCELLED, null, "Transfer cancelled");
        }
        cleanupSession(transferId);
    }

    private void cleanupSession(UUID transferId) {
        repository.clear(transferId);
        publisher.cleanup(transferId);
    }

    private long calculateSpeed(long startTime, long currentBytes) {
        long elapsed = System.currentTimeMillis() - startTime;
        return elapsed > 0 ? (currentBytes * 1000L) / elapsed : 0;
    }

    private long calculateExistingOffset(List<FileInfo> files, Path destDir) {
        long offset = 0;
        for (FileInfo fi : files) {
            File tmp = destDir.resolve(fi.fileName() + ".tmp").toFile();
            if (tmp.exists()) offset += tmp.length();
        }
        return offset;
    }
}