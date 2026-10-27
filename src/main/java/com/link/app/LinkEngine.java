package com.link.app;

import com.link.application.DefaultTransferCoordinator;
import com.link.application.DiscoveryService;
import com.link.application.TransferCoordinator;
import com.link.application.TransferObserver;
import com.link.discovery.UdpDiscoveryService;
import com.link.filesystem.IntegrityService;
import com.link.networking.ConnectionManager;
import com.link.networking.DefaultConnectionManager;
import com.link.networking.DefaultPeerTrustService;
import com.link.networking.PeerTrustService;
import com.link.persistence.DatabaseManager;
import com.link.persistence.TransferRepository;
import com.link.persistence.TrustRepository;
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
    private final PeerTrustService trustService;

    public LinkEngine(String localDisplayName, int port, TransferObserver observer) {
        this.localPeerId = DeviceIdentity.loadOrCreate();
        this.localDisplayName = localDisplayName;
        this.port = port;

        // 1. Persistence Layer Setup
        this.dbManager = new DatabaseManager("link_data.db");
        this.dbManager.initializeDatabase();
        this.transferRepository = new TransferRepository(dbManager);
        
        // Initialize the new SQLite Trust Repository
        TrustRepository trustRepository = new TrustRepository(dbManager);

        // 2. Trust & Security System Setup (Inject the repository)
        this.trustService = new DefaultPeerTrustService(trustRepository);

        // 3. Network Connections & Discovery (Pass the trust service to the connection manager)
        this.connectionManager = new DefaultConnectionManager(port, trustService);
        this.discoveryService = new UdpDiscoveryService(localPeerId, localDisplayName, port);

        // 4. Core Filesystem and Engine Primitives
        IntegrityService integrityService = new IntegrityService();

        com.link.transfer.StreamWorker streamWorker = new com.link.transfer.StreamWorker(integrityService);
        com.link.transfer.TransferProtocolManager protocolManager = new com.link.transfer.TransferProtocolManager();
        com.link.transfer.SessionRepository sessionRepository = new com.link.transfer.SessionRepository();
        com.link.transfer.TransferEventPublisher eventPublisher = new com.link.transfer.TransferEventPublisher(observer);

        this.fileTransferEngine = new com.link.transfer.FileTransferEngine(
            localPeerId,
            localDisplayName,
            discoveryService,
            connectionManager,
            streamWorker,
            protocolManager,
            sessionRepository,
            eventPublisher,
            trustService
        );

        // Route incoming TCP/TLS connections to the Transfer Engine
        this.connectionManager.setIncomingSessionHandler(this.fileTransferEngine::handleIncomingSession);

        // 5. Transfer Coordinator
        this.transferCoordinator = new DefaultTransferCoordinator(
                this.fileTransferEngine,
                this.transferRepository
        );
    }

    public void start() {
        connectionManager.listen();
        discoveryService.start();
        System.out.println("[LinkEngine] Network listener and trust system initialized on port " + port);
    }

    public void stop() {
        discoveryService.stop();
        connectionManager.closeConnection();
        System.out.println("[LinkEngine] Stopped network engine.");
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

    public PeerTrustService getTrustService() {
        return trustService;
    }

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