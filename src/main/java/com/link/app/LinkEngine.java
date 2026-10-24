package com.link.app;

import com.link.application.DefaultTransferCoordinator;
import com.link.application.DiscoveryService;
import com.link.application.TransferCoordinator;
import com.link.application.TransferObserver;
import com.link.discovery.UdpDiscoveryService;
import com.link.networking.ConnectionManager;
import com.link.networking.DefaultConnectionManager;
import com.link.persistence.DatabaseManager;
import com.link.persistence.TransferRepository;
import com.link.transfer.FileTransferEngine;


public class LinkEngine {

    private final String localPeerId;
    private final String localDisplayName;
    private final int port;

    private final DatabaseManager dbManager;
    private final TransferRepository transferRepository;
    private final DiscoveryService discoveryService;
    private final ConnectionManager connectionManager;
    private final TransferCoordinator transferCoordinator;
    private final FileTransferEngine fileTransferEngine;

    public LinkEngine(String localDisplayName, int port, TransferObserver observer) {
        this.localPeerId = DeviceIdentity.loadOrCreate();
        this.localDisplayName = localDisplayName;
        this.port = port;

        // 1. Persistence Layer Setup
        this.dbManager = new DatabaseManager("link_data.db");
        this.dbManager.initializeDatabase();
        this.transferRepository = new TransferRepository(dbManager);

        // 2. Network Connections & Discovery
        this.connectionManager = new DefaultConnectionManager(port);
        this.discoveryService = new UdpDiscoveryService(localPeerId, localDisplayName, port);

        // 3. File Transfer Engine
        // The engine directly handles chunked byte streams for network efficiency
        this.fileTransferEngine = new FileTransferEngine(
          this.localPeerId,
            this.localDisplayName,
            this.discoveryService, 
            this.connectionManager, 
            observer
        );

        // Route incoming TCP connections to the Transfer Engine
        this.connectionManager.setIncomingSessionHandler(this.fileTransferEngine::handleIncomingSession);

        // 4. Transfer Coordinator (Updated to match the new 2-argument constructor)
        this.transferCoordinator = new DefaultTransferCoordinator(
                this.fileTransferEngine,
                this.transferRepository
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

    // Added getters to resolve the "unused field" highlights
    public String getLocalPeerId() {
        return localPeerId;
    }

    public String getLocalDisplayName() {
        return localDisplayName;
    }

    public int getPort() {
        return port;
    }
    public DatabaseManager getDatabaseManager() {
        return dbManager;
    }
}