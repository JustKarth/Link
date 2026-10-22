package com.link.app;

import com.link.application.DefaultTransferCoordinator;
import com.link.application.DiscoveryService;
import com.link.application.TransferCoordinator;
import com.link.application.TransferObserver;
import com.link.discovery.UdpDiscoveryService;
import com.link.filesystem.FileStorageService;
import com.link.filesystem.IntegrityService;
import com.link.networking.ConnectionManager;
import com.link.networking.DefaultConnectionManager;
import com.link.persistence.DatabaseManager;
import com.link.persistence.TransferRepository;
import com.link.transfer.FileTransferEngine;

import java.util.UUID;

/**
 * Main backend application manager that initializes and wires core services.
 */
public class LinkEngine {

    private final String localPeerId;
    private final String localDisplayName;
    private final int port;

    private final DatabaseManager dbManager;
    private final TransferRepository transferRepository;
    private final DiscoveryService discoveryService;
    private final ConnectionManager connectionManager;
    private final TransferCoordinator transferCoordinator;

    public LinkEngine(String localDisplayName, int port, TransferObserver observer) {
        this.localPeerId = UUID.randomUUID().toString();
        this.localDisplayName = localDisplayName;
        this.port = port;

        // 1. Persistence Layer Setup
        this.dbManager = new DatabaseManager("link_data.db");
        this.dbManager.initializeDatabase();
        this.transferRepository = new TransferRepository(dbManager);

        // 2. Core Filesystem and Engine Primitives
        FileStorageService storageService = new FileStorageService();
        IntegrityService integrityService = new IntegrityService();
        FileTransferEngine transferEngine = new FileTransferEngine();

        // 3. Application and Network Services
        this.discoveryService = new UdpDiscoveryService(localPeerId, localDisplayName, port);
        this.connectionManager = new DefaultConnectionManager(port);
        this.transferCoordinator = new DefaultTransferCoordinator(
                storageService,
                integrityService,
                transferEngine,
                observer,
                transferRepository
        );
    }

    public void start() {
        connectionManager.listen();
        discoveryService.start();
    }

    public void stop() {
        discoveryService.stop();
        connectionManager.closeConnection();
    }

    public DiscoveryService getDiscoveryService() {
        return discoveryService;
    }

    public TransferCoordinator getTransferCoordinator() {
        return transferCoordinator;
    }

    public TransferRepository getTransferRepository() {
        return transferRepository;
    }
}