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
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class FileTransferEngine {

    private static final long PAIRING_TIMEOUT_SECONDS = 60;

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
    private final ScheduledExecutorService pairingScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Link-PairingTimeoutScheduler");
        t.setDaemon(true);
        return t;
    });

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
            SecureSession session = null;
            
            try {
                final Peer targetPeer = discoveryService.getDiscoveredPeers().stream()
                        .filter(p -> p.id().equals(peerId)).findFirst()
                        .orElseThrow(() -> new RuntimeException("Peer not found"));

                publisher.publish(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.VERIFYING, new TransferProgress(0, 0, 0), null);

                session = connectionManager.connect(targetPeer);
                
                String peerFingerprint = session.getRemoteFingerprint();
                boolean senderTrustsPeer = trustService.isPeerTrusted(targetPeer.id().value(), peerFingerprint);

                // 1. Initial pairing/identity hello
                ControlMessage hello = new ControlMessage(
                    "HANDSHAKE_HELLO",
                    transferId.toString(),
                    summaryName,
                    0,
                    null,
                    localPeerId,
                    localDisplayName,
                    null,
                    0,
                    senderTrustsPeer
                );
                protocolManager.sendMessage(session, hello);

                // 2. Wait for receiver handshake response
                ControlMessage response = protocolManager.receiveMessage(session);
                if (!"HANDSHAKE_RESPONSE".equals(response.type())) {
                    throw new IOException("Unexpected handshake response: " + response.type());
                }

                boolean receiverTrustsPeer = response.senderTrustsPeer();
                boolean pairingRequired = (!senderTrustsPeer || !receiverTrustsPeer);

                if (pairingRequired) {
                    boolean paired = performPairing(transferId, session, targetPeer, peerFingerprint, TransferDirection.SENDING, sourcePaths, summaryName);
                    if (!paired) {
                        return;
                    }
                }

                proceedWithOutgoingTransfer(transferId, targetPeer, session, sourcePaths);

            } catch (Exception e) {
                System.err.println(
                    "[Link] Outgoing setup failed. Transfer=" + transferId
                    + ", peer=" + peerId.value()
                );
                e.printStackTrace(System.err);

                if (session != null) {
                    session.close();
                }

                publisher.publish(
                    transferId,
                    summaryName,
                    TransferDirection.SENDING,
                    null,
                    TransferState.FAILED,
                    new TransferProgress(0, 0, 0),
                    e.getMessage()
                );

                cleanupSession(transferId);
            }
        });
    }

    public void confirmPairing(UUID transferId, boolean accepted) {
        transferPool.submit(() -> {
            SessionRepository.PairingPendingState pending = repository.getPendingPairing(transferId);
            if (pending != null) {
                if (!accepted) {
                    repository.removePendingPairing(transferId);
                    try {
                        protocolManager.sendMessage(pending.session(), 
                            new ControlMessage("PAIRING_REJECT", transferId.toString(), null, 0, null, localPeerId, localDisplayName, null, 0));
                    } catch (Exception ignored) {}

                    pending.cancel();
                    publisher.publish(transferId, pending.summaryName(), pending.direction(), pending.peer(), 
                        TransferState.CANCELLED, null, "Pairing rejected by local user", null);
                } else {
                    pending.localDecision().complete(true);
                    try {
                        protocolManager.sendMessage(pending.session(), 
                            new ControlMessage("PAIRING_ACCEPT", transferId.toString(), null, 0, null, localPeerId, localDisplayName, null, 0));
                    } catch (Exception e) {
                        repository.removePendingPairing(transferId);
                        pending.cancel();
                        publisher.publish(transferId, pending.summaryName(), pending.direction(), pending.peer(),
                            TransferState.FAILED, null, "Connection lost during pairing", null);
                    }
                }
                return;
            }

            // Legacy fallback if any old state exists
            SessionRepository.OutgoingPendingState pendingOut = repository.getAndRemovePendingOutgoing(transferId);
            if (pendingOut != null) {
                if (accepted) {
                    trustService.trustPeer(pendingOut.targetPeer().id().value(), pendingOut.fingerprint());
                    proceedWithOutgoingTransfer(transferId, pendingOut.targetPeer(), pendingOut.session(), pendingOut.sourcePaths());
                } else {
                    pendingOut.session().close();
                    publisher.publish(transferId, "Unknown", TransferDirection.SENDING, pendingOut.targetPeer(), TransferState.CANCELLED, null, "Pairing rejected", null);
                }
                return;
            }

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
                ControlMessage msg = protocolManager.receiveMessage(session);

                if ("TRANSFER_REQUEST".equals(msg.type())) {
                    handleLegacyIncomingTransfer(session, msg);
                    return;
                }

                if (!"HANDSHAKE_HELLO".equals(msg.type())) {
                    throw new IOException("Unexpected handshake message: " + msg.type());
                }

                UUID transferId;
                try {
                    transferId = UUID.fromString(msg.transferId());
                } catch (Exception e) {
                    throw new IOException("Invalid transferId in handshake: " + msg.transferId());
                }

                String senderId = msg.senderId();
                String senderDisplayName = msg.senderDisplayName();
                String summaryName = msg.fileName() != null ? msg.fileName() : "Incoming transfer";
                boolean senderTrustsPeer = msg.senderTrustsPeer();

                String peerFingerprint = session.getRemoteFingerprint();
                boolean receiverTrustsPeer = trustService.isPeerTrusted(senderId, peerFingerprint);

                Peer senderPeer = new Peer(new PeerId(senderId), senderDisplayName, "", false);

                boolean pairingRequired = (!senderTrustsPeer || !receiverTrustsPeer);

                // Send handshake response acknowledging trust state
                ControlMessage response = new ControlMessage(
                    "HANDSHAKE_RESPONSE",
                    transferId.toString(),
                    null,
                    0,
                    null,
                    localPeerId,
                    localDisplayName,
                    null,
                    0,
                    receiverTrustsPeer
                );
                protocolManager.sendMessage(session, response);

                if (pairingRequired) {
                    boolean paired = performPairing(transferId, session, senderPeer, peerFingerprint, TransferDirection.RECEIVING, null, summaryName);
                    if (!paired) {
                        return;
                    }
                }

                // Proceed to read the TRANSFER_REQUEST on the same session
                ControlMessage request = protocolManager.receiveMessage(session);
                if (!"TRANSFER_REQUEST".equals(request.type())) {
                    throw new IOException("Expected TRANSFER_REQUEST, got: " + request.type());
                }

                proceedWithIncomingTransfer(transferId, request, session);

            } catch (Exception e) {
                System.err.println("[Link] Incoming session failed: " + e.getMessage());
                e.printStackTrace(System.err);
                session.close();
            }
        });
    }

    private void handleLegacyIncomingTransfer(SecureSession session, ControlMessage request) {
        UUID transferId = UUID.fromString(request.transferId());
        String peerFingerprint = session.getRemoteFingerprint();
        String senderId = request.senderId();

        if (!trustService.isPeerTrusted(senderId, peerFingerprint)) {
            String sasCode = session.getSasPairingCode();
            repository.addPendingIncoming(transferId, new SessionRepository.IncomingPendingState(request, session, peerFingerprint));
            Peer senderPeer = new Peer(new PeerId(senderId), request.senderDisplayName(), "", false);
            publisher.publish(transferId, request.fileName(), TransferDirection.RECEIVING, senderPeer, TransferState.PAIRING, new TransferProgress(0, 0, 0), null, sasCode);
            return;
        }

        proceedWithIncomingTransfer(transferId, request, session);
    }

    private boolean performPairing(
            UUID transferId, 
            SecureSession session, 
            Peer peer, 
            String peerFingerprint, 
            TransferDirection direction, 
            List<Path> sourcePaths, 
            String summaryName) {

        String sasCode = session.getSasPairingCode();
        SessionRepository.PairingPendingState state = new SessionRepository.PairingPendingState(
            transferId, session, peer, peerFingerprint, direction, sourcePaths, summaryName
        );
        repository.addPendingPairing(transferId, state);

        ScheduledFuture<?> timeoutFuture = pairingScheduler.schedule(() -> {
            SessionRepository.PairingPendingState p = repository.removePendingPairing(transferId);
            if (p != null) {
                p.cancel();
                publisher.publish(transferId, summaryName, direction, peer, TransferState.FAILED, 
                    new TransferProgress(0, 0, 0), "Pairing timed out", null);
            }
        }, PAIRING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        state.setTimeoutFuture(timeoutFuture);

        // Publish PAIRING state so both devices display the SAS code simultaneously
        publisher.publish(transferId, summaryName, direction, peer, TransferState.PAIRING, 
            new TransferProgress(0, 0, 0), null, sasCode);

        // Exactly one reader on the session: read remote peer's decision
        boolean remoteAccepted = false;
        try {
            ControlMessage remoteMsg = protocolManager.receiveMessage(session);
            remoteAccepted = "PAIRING_ACCEPT".equals(remoteMsg.type());
        } catch (Exception e) {
            remoteAccepted = false;
        }

        if (!remoteAccepted) {
            SessionRepository.PairingPendingState p = repository.removePendingPairing(transferId);
            if (p != null) {
                p.cancel();
                publisher.publish(transferId, summaryName, direction, peer, TransferState.CANCELLED, 
                    null, "Pairing rejected by peer", null);
            }
            return false;
        }

        // Remote peer accepted; wait for local user's decision
        Boolean localAccepted = false;
        try {
            localAccepted = state.localDecision().get(PAIRING_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            localAccepted = false;
        }

        if (!Boolean.TRUE.equals(localAccepted)) {
            SessionRepository.PairingPendingState p = repository.removePendingPairing(transferId);
            if (p != null) {
                p.cancel();
                publisher.publish(transferId, summaryName, direction, peer, TransferState.CANCELLED, 
                    null, "Pairing rejected", null);
            }
            return false;
        }

        // Both accepted!
        SessionRepository.PairingPendingState p = repository.removePendingPairing(transferId);
        if (p != null) {
            p.completeSuccess();
        } else {
            return false;
        }

        // Persist permanent trust only after mutual positive confirmation
        trustService.trustPeer(peer.id().value(), peerFingerprint);
        return true;
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
        SessionRepository.PairingPendingState pairing = repository.removePendingPairing(transferId);
        if (pairing != null) {
            try {
                protocolManager.sendMessage(pairing.session(), 
                    new ControlMessage("PAIRING_REJECT", transferId.toString(), null, 0, null, localPeerId, localDisplayName, null, 0));
            } catch (Exception ignored) {}
            pairing.cancel();
            publisher.publish(transferId, pairing.summaryName(), pairing.direction(), pairing.peer(), 
                TransferState.CANCELLED, null, "Transfer cancelled", null);
        }
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

    public void pauseTransfer(UUID transferId) {
        SessionRepository.PauseHandle handle = repository.getPauseHandle(transferId);
        if (handle == null || handle.isPaused()) return;

        handle.pause();

        publisher.publish(transferId, null, TransferDirection.SENDING, null,
                TransferState.PAUSED, null, null);
    }

    public void resumeTransfer(UUID transferId) {
        SessionRepository.PauseHandle handle = repository.getPauseHandle(transferId);
        if (handle == null || !handle.isPaused()) return;

        handle.resume();

        publisher.publish(transferId, null, TransferDirection.SENDING, null,
                TransferState.TRANSFERRING, null, null);
    }

    private void cleanupSession(UUID transferId) {
        SecureSession session = repository.getSession(transferId);
        if (session != null) {
            session.close();
        }
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