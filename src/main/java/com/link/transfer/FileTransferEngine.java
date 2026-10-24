package com.link.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.link.application.DiscoveryService;
import com.link.application.TransferObserver;
import com.link.domain.Peer;
import com.link.domain.PeerId;
import com.link.domain.TransferDirection;
import com.link.domain.TransferProgress;
import com.link.domain.TransferSession;
import com.link.domain.TransferState;
import com.link.filesystem.IntegrityService;
import com.link.networking.ConnectionManager;
import com.link.networking.SecureSession;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileTransferEngine {

    private final String localPeerId;
    private final String localDisplayName;
    private final DiscoveryService discoveryService;
    private final ConnectionManager connectionManager;
    private final IntegrityService integrityService;
    private final TransferObserver observer;
    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorService transferPool = Executors.newCachedThreadPool();
    
    private final Map<UUID, SecureSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, IncomingBatchState> incomingBatches = new ConcurrentHashMap<>();

    public record FileInfo(String fileName, long fileSize, String checksum) {}

    public record ControlMessage(
        String type, 
        String transferId, 
        String fileName, 
        long fileSize,
        List<FileInfo> files,
        String senderId,
        String senderDisplayName,
        String checksum
    ) {}
    
    private record IncomingBatchState(List<FileInfo> files, long totalBatchSize, Peer peer) {}

    public FileTransferEngine(
            String localPeerId,
            String localDisplayName,
            DiscoveryService discoveryService, 
            ConnectionManager connectionManager, 
            IntegrityService integrityService,
            TransferObserver observer) {
        this.localPeerId = localPeerId;
        this.localDisplayName = localDisplayName;
        this.discoveryService = discoveryService;
        this.connectionManager = connectionManager;
        this.integrityService = integrityService;
        this.observer = observer;
    }

    public void initiateTransfer(UUID transferId, PeerId peerId, List<Path> sourcePaths) {
        transferPool.submit(() -> {
            Peer targetPeer = null;
            
            String summaryName = sourcePaths.size() == 1 
                    ? sourcePaths.get(0).getFileName().toString() 
                    : sourcePaths.size() + " files";

            try {
                targetPeer = discoveryService.getDiscoveredPeers().stream()
                        .filter(p -> p.id().equals(peerId))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Peer not found"));

                // 1. Calculate checksums and build manifest for all files
                notifyUI(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.VERIFYING, new TransferProgress(0, 0, 0), null);
                
                List<FileInfo> fileManifest = new ArrayList<>();
                long totalBatchBytes = 0;

                for (Path p : sourcePaths) {
                    File file = p.toFile();
                    if (!file.exists() || !file.isFile()) continue;
                    String checksum = integrityService.calculateSHA256(p);
                    long len = file.length();
                    totalBatchBytes += len;
                    fileManifest.add(new FileInfo(file.getName(), len, checksum));
                }

                if (fileManifest.isEmpty()) {
                    throw new IllegalArgumentException("No valid files selected for transfer");
                }

                SecureSession session = connectionManager.connect(targetPeer);
                activeSessions.put(transferId, session);
                
                // 2. Transmit batch request manifest
                ControlMessage request = new ControlMessage(
                    "TRANSFER_REQUEST", 
                    transferId.toString(), 
                    summaryName, 
                    totalBatchBytes,
                    fileManifest,
                    localPeerId,
                    localDisplayName,
                    null
                );
                session.sendMessage(mapper.writeValueAsString(request));

                String responseJson = session.receiveMessage();
                ControlMessage response = mapper.readValue(responseJson, ControlMessage.class);

                if ("TRANSFER_ACCEPTED".equals(response.type())) {
                    notifyUI(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.TRANSFERRING, new TransferProgress(totalBatchBytes, 0, 0), null);

                    long overallCompletedBytes = 0;
                    long startTime = System.currentTimeMillis();

                    // 3. Sequential streaming of batch files
                    for (int i = 0; i < sourcePaths.size(); i++;) {
                        File file = sourcePaths.get(i).toFile();
                        if (!file.exists() || !file.isFile()) continue;

                        try (FileInputStream fis = new FileInputStream(file)) {
                            byte[] buffer = new byte[8192];
                            int read;
                            while ((read = fis.read(buffer)) > 0) {
                                if (read == buffer.length) {
                                    session.sendData(buffer);
                                } else {
                                    byte[] exactChunk = new byte[read];
                                    System.arraycopy(buffer, 0, exactChunk, 0, read);
                                    session.sendData(exactChunk);
                                }
                                
                                overallCompletedBytes += read;
                                long elapsedMs = System.currentTimeMillis() - startTime;
                                long speed = elapsedMs > 0 ? (overallCompletedBytes * 1000L) / elapsedMs : 0;
                                
                                notifyUI(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.TRANSFERRING, new TransferProgress(totalBatchBytes, overallCompletedBytes, speed), null);
                            }
                        }
                    }

                    ControlMessage complete = new ControlMessage("TRANSFER_COMPLETED", transferId.toString(), null, 0, null, null, null, null);
                    session.sendMessage(mapper.writeValueAsString(complete));
                    
                    notifyUI(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.COMPLETED, new TransferProgress(totalBatchBytes, totalBatchBytes, 0), null);
                } else if ("TRANSFER_REJECTED".equals(response.type())) {
                    notifyUI(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.FAILED, new TransferProgress(totalBatchBytes, 0, 0), "Peer rejected transfer");
                }

            } catch (Exception e) {
                e.printStackTrace();
                notifyUI(transferId, summaryName, TransferDirection.SENDING, targetPeer, TransferState.FAILED, new TransferProgress(0, 0, 0), e.getMessage());
            } finally {
                closeSession(transferId);
            }
        });
    }

    public void handleIncomingSession(SecureSession session) {
        transferPool.submit(() -> {
            try {
                String requestJson = session.receiveMessage();
                ControlMessage request = mapper.readValue(requestJson, ControlMessage.class);

                if ("TRANSFER_REQUEST".equals(request.type())) {
                    UUID transferId = UUID.fromString(request.transferId());
                    activeSessions.put(transferId, session);
                    
                    Peer senderPeer = new Peer(
                        new PeerId(request.senderId() != null ? request.senderId() : "unknown-id"),
                        request.senderDisplayName() != null ? request.senderDisplayName() : "Unknown",
                        "",
                        false
                    );

                    List<FileInfo> files = request.files() != null && !request.files().isEmpty()
                            ? request.files()
                            : List.of(new FileInfo(request.fileName(), request.fileSize(), request.checksum()));

                    long totalBatchSize = files.stream().mapToLong(FileInfo::fileSize).sum();

                    incomingBatches.put(transferId, new IncomingBatchState(files, totalBatchSize, senderPeer));
                    
                    notifyUI(transferId, request.fileName(), TransferDirection.RECEIVING, senderPeer, TransferState.OFFERED, new TransferProgress(totalBatchSize, 0, 0), null);
                }
            } catch (Exception e) {
                e.printStackTrace();
                session.close();
            }
        });
    }

    public void acceptTransfer(UUID transferId, Path destinationDirectory) {
        transferPool.submit(() -> {
            SecureSession session = activeSessions.get(transferId);
            IncomingBatchState batchState = incomingBatches.get(transferId);
            if (session == null || batchState == null) return;

            String summaryName = batchState.files().size() == 1 
                    ? batchState.files().get(0).fileName() 
                    : batchState.files().size() + " files";

            try {
                ControlMessage acceptMsg = new ControlMessage("TRANSFER_ACCEPTED", transferId.toString(), null, 0, null, null, null, null);
                session.sendMessage(mapper.writeValueAsString(acceptMsg));

                notifyUI(transferId, summaryName, TransferDirection.RECEIVING, batchState.peer(), TransferState.TRANSFERRING, new TransferProgress(batchState.totalBatchSize(), 0, 0), null);

                long overallReceived = 0;
                long startTime = System.currentTimeMillis();
                List<File> receivedFiles = new ArrayList<>();

                for (FileInfo fi : batchState.files()) {
                    File destFile = destinationDirectory.resolve(fi.fileName()).toFile();
                    receivedFiles.add(destFile);

                    try (FileOutputStream fos = new FileOutputStream(destFile)) {
                        long fileReceived = 0;
                        while (fileReceived < fi.fileSize()) {
                            byte[] chunk = session.receiveData();
                            
                            if (chunk.length > 0 && chunk[0] == '{') {
                                String maybeJson = new String(chunk);
                                if (maybeJson.contains("TRANSFER_COMPLETED")) break;
                            }
                            
                            fos.write(chunk);
                            fileReceived += chunk.length;
                            overallReceived += chunk.length;

                            long elapsedMs = System.currentTimeMillis() - startTime;
                            long speed = elapsedMs > 0 ? (overallReceived * 1000L) / elapsedMs : 0;

                            notifyUI(transferId, summaryName, TransferDirection.RECEIVING, batchState.peer(), TransferState.TRANSFERRING, new TransferProgress(batchState.totalBatchSize(), overallReceived, speed), null);
                        }
                    }
                }
                
                // Integrity verification pass for all received items
                notifyUI(transferId, summaryName, TransferDirection.RECEIVING, batchState.peer(), TransferState.VERIFYING, new TransferProgress(batchState.totalBatchSize(), batchState.totalBatchSize(), 0), null);
                
                boolean allValid = true;
                for (int i = 0; i < batchState.files().size(); i++) {
                    FileInfo fi = batchState.files().get(i);
                    File f = receivedFiles.get(i);
                    if (!integrityService.verifyFile(f.toPath(), fi.checksum())) {
                        allValid = false;
                        break;
                    }
                }

                if (allValid) {
                    notifyUI(transferId, summaryName, TransferDirection.RECEIVING, batchState.peer(), TransferState.COMPLETED, new TransferProgress(batchState.totalBatchSize(), batchState.totalBatchSize(), 0), null);
                } else {
                    notifyUI(transferId, summaryName, TransferDirection.RECEIVING, batchState.peer(), TransferState.FAILED, new TransferProgress(batchState.totalBatchSize(), batchState.totalBatchSize(), 0), "Checksum mismatch: Batch corrupted during transfer.");
                }
                
            } catch (Exception e) {
                e.printStackTrace();
                notifyUI(transferId, summaryName, TransferDirection.RECEIVING, batchState.peer(), TransferState.FAILED, new TransferProgress(batchState.totalBatchSize(), 0, 0), e.getMessage());
            } finally {
                closeSession(transferId);
                incomingBatches.remove(transferId);
            }
        });
    }

    public void rejectTransfer(UUID transferId) {
        transferPool.submit(() -> {
            SecureSession session = activeSessions.get(transferId);
            IncomingBatchState batchState = incomingBatches.get(transferId);
            String summaryName = batchState != null ? (batchState.files().size() == 1 ? batchState.files().get(0).fileName() : batchState.files().size() + " files") : "Unknown";
            Peer peer = batchState != null ? batchState.peer() : null;

            if (session != null) {
                try {
                    ControlMessage rejectMsg = new ControlMessage("TRANSFER_REJECTED", transferId.toString(), null, 0, null, null, null, null);
                    session.sendMessage(mapper.writeValueAsString(rejectMsg));
                    notifyUI(transferId, summaryName, TransferDirection.RECEIVING, peer, TransferState.CANCELLED, null, "Transfer rejected by local user");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    closeSession(transferId);
                    incomingBatches.remove(transferId);
                }
            }
        });
    }

    public void cancelTransfer(UUID transferId) {
        IncomingBatchState batchState = incomingBatches.get(transferId);
        String summaryName = batchState != null ? (batchState.files().size() == 1 ? batchState.files().get(0).fileName() : batchState.files().size() + " files") : "Unknown";
        Peer peer = batchState != null ? batchState.peer() : null;

        closeSession(transferId);
        incomingBatches.remove(transferId);
        notifyUI(transferId, summaryName, TransferDirection.SENDING, peer, TransferState.CANCELLED, null, "Transfer cancelled");
    }

    private void closeSession(UUID transferId) {
        SecureSession session = activeSessions.remove(transferId);
        if (session != null) {
            session.close();
        }
    }

    private void notifyUI(UUID transferId, String fileName, TransferDirection direction, Peer peer, TransferState state, TransferProgress progress, String failureReason) {
        TransferSession snapshot = new TransferSession(transferId, fileName, direction, peer, state, progress, failureReason);
        observer.onTransferUpdated(snapshot);
    }
}