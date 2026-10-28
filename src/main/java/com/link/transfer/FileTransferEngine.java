package com.link.transfer;

import com.link.application.DiscoveryService;
import com.link.domain.Peer;
import com.link.domain.PeerId;
import com.link.domain.TransferDirection;
import com.link.domain.TransferProgress;
import com.link.domain.TransferState;
import com.link.networking.ConnectionManager;
import com.link.networking.PeerTrustService;
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
    private final PeerTrustService trustService;
    
    private final ExecutorService transferPool = Executors.newCachedThreadPool();

    public FileTransferEngine(
            String localPeerId, String localDisplayName,
            DiscoveryService discoveryService, ConnectionManager connectionManager, 
            StreamWorker streamWorker, TransferProtocolManager protocolManager,
            SessionRepository repository, TransferEventPublisher publisher,
            PeerTrustService trustService) {
        
        this.localPeerId = localPeerId;
        this.localDisplayName = localDisplayName;
        this.discoveryService = discoveryService;
        this.connectionManager = connectionManager;
        this.streamWorker = streamWorker;
        this.protocolManager = protocolManager;
        this.repository = repository;
        this.publisher = publisher;
        this.trustService = trustService;
    }

    public void initiateTransfer(UUID transferId, PeerId peerId, List<Path> sourcePaths) {
        transferPool.submit(() -> {
            String summaryName = sourcePaths.size() == 1 ? sourcePaths.get(0).getFileName().toString() : sourcePaths.size() + " files";
            
            try {
                final Peer targetPeer = discoveryService.getDiscoveredPeers().stream()
                        .filter(p -> p.id().equals(peerId)).findFirst()
                        .orElseThrow(() -> new RuntimeException("Peer not found"));

                publisher.publish(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.VERIFYING, new TransferProgress(0, 0, 0), null);

                SecureSession session = connectionManager.connect(targetPeer);
                
                // --- SECURITY HANDSHAKE ---
                String peerFingerprint = session.getRemoteFingerprint();

                if (!trustService.isPeerTrusted(targetPeer.id().toString(), peerFingerprint)) {
                    String sasCode = session.getSasPairingCode();
                    
                    // Save state and pause thread
                    repository.addPendingOutgoing(transferId, new SessionRepository.OutgoingPendingState(targetPeer, session, sourcePaths, peerFingerprint));
                    
                    // Publish PAIRING state with the SAS code (assuming you added it to publisher args)
                    publisher.publish(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.PAIRING, new TransferProgress(0, 0, 0), null, sasCode);
                    return; // Exit thread. Engine is now paused.
                }

                // If already trusted, proceed immediately
                proceedWithOutgoingTransfer(transferId, targetPeer, session, sourcePaths);

            } catch (Exception e) {
                publisher.publish(transferId, summaryName, TransferDirection.SENDING, null, TransferState.FAILED, new TransferProgress(0, 0, 0), e.getMessage());
                cleanupSession(transferId);
            }
        });
    }

    public void confirmPairing(UUID transferId, boolean accepted) {
        transferPool.submit(() -> {
            // Check Outgoing
            SessionRepository.OutgoingPendingState pendingOut = repository.getAndRemovePendingOutgoing(transferId);
            if (pendingOut != null) {
                if (accepted) {
                    trustService.trustPeer(pendingOut.targetPeer().id().toString(), pendingOut.fingerprint());
                    proceedWithOutgoingTransfer(transferId, pendingOut.targetPeer(), pendingOut.session(), pendingOut.sourcePaths());
                } else {
                    pendingOut.session().close();
                    publisher.publish(transferId, "Unknown", TransferDirection.SENDING, pendingOut.targetPeer(), TransferState.CANCELLED, null, "Pairing rejected", null);
                }
                return;
            }

            // Check Incoming
            SessionRepository.IncomingPendingState pendingIn = repository.getAndRemovePendingIncoming(transferId);
            if (pendingIn != null) {
                if (accepted) {
                    trustService.trustPeer(pendingIn.request().senderId(), pendingIn.fingerprint());
                    proceedWithIncomingTransfer(transferId, pendingIn.request(), pendingIn.session());
                } else {
                    try {
                        protocolManager.sendMessage(pendingIn.session(), new ControlMessage("TRANSFER_REJECTED", transferId.toString(), null, 0, null, null, null, null, 0));
                    } catch (Exception ignored) {}
                    
                    pendingIn.session().close();
                    Peer senderPeer = new Peer(new PeerId(pendingIn.request().senderId()), pendingIn.request().senderDisplayName(), "", false);
                    publisher.publish(transferId, pendingIn.request().fileName(), TransferDirection.RECEIVING, senderPeer, TransferState.CANCELLED, null, "Pairing rejected", null);
                }
            }
        });
    }

    public void handleIncomingSession(SecureSession session) {
        transferPool.submit(() -> {
            try {
                ControlMessage request = protocolManager.receiveMessage(session);

                if ("TRANSFER_REQUEST".equals(request.type())) {
                    UUID transferId = UUID.fromString(request.transferId());
                    
                    // --- SECURITY HANDSHAKE ---
                    String peerFingerprint = session.getRemoteFingerprint();
                    String senderId = request.senderId();

                    if (!trustService.isPeerTrusted(senderId, peerFingerprint)) {
                        String sasCode = session.getSasPairingCode();
                        
                        // Save state and pause thread
                        repository.addPendingIncoming(transferId, new SessionRepository.IncomingPendingState(request, session, peerFingerprint));
                        
                        Peer senderPeer = new Peer(new PeerId(senderId), request.senderDisplayName(), "", false);
                        
                        // Publish PAIRING state with the SAS code
                        publisher.publish(transferId, request.fileName(), TransferDirection.RECEIVING, senderPeer, TransferState.PAIRING, new TransferProgress(0, 0, 0), null, sasCode);
                        return; // Exit thread. Engine is now paused.
                    }

                    // If already trusted, proceed immediately
                    proceedWithIncomingTransfer(transferId, request, session);
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

    private void proceedWithIncomingTransfer(UUID transferId, ControlMessage request, SecureSession session) {
        repository.addSession(transferId, session);
                    
        Peer senderPeer = new Peer(new PeerId(request.senderId()), request.senderDisplayName(), "", false);
        List<FileInfo> files = request.files() != null ? request.files() : List.of(new FileInfo(request.fileName(), request.fileSize(), request.checksum()));
        long totalSize = files.stream().mapToLong(FileInfo::fileSize).sum();

        repository.addBatch(transferId, new IncomingBatchState(files, totalSize, senderPeer, null));
        publisher.publish(transferId, request.fileName(), TransferDirection.RECEIVING, senderPeer, TransferState.OFFERED, new TransferProgress(totalSize, 0, 0), null, null);
    }

    private void proceedWithOutgoingTransfer(UUID transferId, Peer targetPeer, SecureSession session, List<Path> sourcePaths) {
        String summaryName = sourcePaths.size() == 1 ? sourcePaths.get(0).getFileName().toString() : sourcePaths.size() + " files";
        try {
            List<FileInfo> fileManifest = streamWorker.createFileManifest(sourcePaths);
            if (fileManifest.isEmpty()) throw new IllegalArgumentException("No valid files selected");
            long totalBatchBytes = fileManifest.stream().mapToLong(FileInfo::fileSize).sum();

            repository.addSession(transferId, session);

            // Create a pause handle so pauseTransfer()/resumeTransfer() can
            // signal the sender thread mid-stream.
            SessionRepository.PauseHandle pauseHandle = repository.createPauseHandle(transferId);
            
            ControlMessage request = new ControlMessage("TRANSFER_REQUEST", transferId.toString(), summaryName, totalBatchBytes, fileManifest, localPeerId, localDisplayName, null, 0);
            protocolManager.sendMessage(session, request);

            ControlMessage response = protocolManager.receiveMessage(session);

            if ("TRANSFER_ACCEPTED".equals(response.type())) {
                long resumeOffset = response.offset();
                publisher.publish(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.TRANSFERRING, new TransferProgress(totalBatchBytes, resumeOffset, 0), null);

                long startTime = System.currentTimeMillis();
                
                streamWorker.sendFiles(session, sourcePaths, resumeOffset, pauseHandle, (bytesSent) -> {
                    long speed = calculateSpeed(startTime, bytesSent);
                    publisher.publishThrottled(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.TRANSFERRING, new TransferProgress(totalBatchBytes, bytesSent, speed));
                });

                protocolManager.sendMessage(session, new ControlMessage("TRANSFER_COMPLETED", transferId.toString(), null, 0, null, null, null, null, 0));
                publisher.publish(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.COMPLETED, new TransferProgress(totalBatchBytes, totalBatchBytes, 0), null);
                
            } else if ("TRANSFER_REJECTED".equals(response.type())) {
                publisher.publish(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.FAILED, new TransferProgress(totalBatchBytes, 0, 0), "Peer rejected transfer");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            publisher.publish(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.CANCELLED, new TransferProgress(0, 0, 0), "Transfer interrupted");
        } catch (Exception e) {
            publisher.publish(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.FAILED, new TransferProgress(0, 0, 0), e.getMessage());
        } finally {
            cleanupSession(transferId);
        }
    }

    /**
     * Pauses an active outgoing transfer. The sender thread will stop sending
     * after the current chunk completes and block until {@link #resumeTransfer}
     * is called. On the receiver side the socket stays open and the receive
     * loop simply blocks on the next {@code receiveData()} call — no extra
     * signaling needed on that side.
     *
     * <p>Only affects outgoing (sender-side) transfers; incoming transfers
     * are paused implicitly because no data arrives.
     */
    public void pauseTransfer(UUID transferId) {
        SessionRepository.PauseHandle handle = repository.getPauseHandle(transferId);
        if (handle == null || handle.isPaused()) return;

        handle.pause();

        // Reflect the PAUSED state in the UI via the publisher.
        IncomingBatchState batch = repository.getBatch(transferId);
        String summaryName = batch != null
                ? (batch.files().size() == 1 ? batch.files().get(0).fileName() : batch.files().size() + " files")
                : "Transfer";
        // Peer info is carried in the batch for incoming; for outgoing we
        // publish with a null peer — the observer already has the peer from
        // previous TRANSFERRING events.
        publisher.publish(transferId, summaryName, TransferDirection.SENDING, null,
                TransferState.PAUSED, new TransferProgress(0, 0, 0), null);
    }

    /**
     * Resumes a previously paused outgoing transfer. The blocked sender thread
     * is released and data flow restarts from where it stopped.
     */
    public void resumeTransfer(UUID transferId) {
        SessionRepository.PauseHandle handle = repository.getPauseHandle(transferId);
        if (handle == null || !handle.isPaused()) return;

        handle.resume();

        IncomingBatchState batch = repository.getBatch(transferId);
        String summaryName = batch != null
                ? (batch.files().size() == 1 ? batch.files().get(0).fileName() : batch.files().size() + " files")
                : "Transfer";
        publisher.publish(transferId, summaryName, TransferDirection.SENDING, null,
                TransferState.TRANSFERRING, new TransferProgress(0, 0, 0), null);
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