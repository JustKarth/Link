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
import com.link.networking.ConnectionManager;
import com.link.networking.PeerTrustService;
import com.link.networking.SecureSession;
import com.link.transfer.models.ControlMessage;
import com.link.transfer.models.FileInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class FileTransferEnginePairingTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // In-memory fake sessions
    static class FakeSecureSession implements SecureSession {
        final BlockingQueue<String> incoming = new LinkedBlockingQueue<>();
        volatile FakeSecureSession peerSession;
        final String remoteFingerprint;
        final String sasCode;
        volatile boolean closed = false;

        FakeSecureSession(String remoteFingerprint, String sasCode) {
            this.remoteFingerprint = remoteFingerprint;
            this.sasCode = sasCode;
        }

        void setPeerSession(FakeSecureSession peerSession) {
            this.peerSession = peerSession;
        }

        @Override public void authenticatePeer() {}
        @Override public String getRemoteFingerprint() { return remoteFingerprint; }
        @Override public String getSasPairingCode() { return sasCode; }

        @Override
        public void sendMessage(String messagePayload) {
            if (closed) throw new RuntimeException("Session closed");
            peerSession.incoming.offer(messagePayload);
        }

        @Override
        public String receiveMessage() {
            try {
                String msg = incoming.poll(5, TimeUnit.SECONDS);
                if (msg == null) throw new RuntimeException("Timeout reading frame");
                return msg;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        @Override public void sendData(byte[] data) {}
        @Override public byte[] receiveData() { return new byte[0]; }
        @Override public void close() { closed = true; }
    }

    static class FakeTrustService implements PeerTrustService {
        final Map<String, String> trusted = new ConcurrentHashMap<>();

        @Override public String computeFingerprint(Certificate cert) { return "fp"; }
        @Override public String generateSASCode(String p1, String p2) { return "654321"; }
        @Override public boolean isPeerTrusted(String peerId, String fingerprint) {
            String existing = trusted.get(peerId);
            return existing != null && existing.equals(fingerprint);
        }
        @Override public void trustPeer(String peerId, String fingerprint) {
            trusted.put(peerId, fingerprint);
        }
        @Override public void revokeTrust(String peerId) {
            trusted.remove(peerId);
        }
    }

    static class FakeStreamWorker extends StreamWorker {
        public FakeStreamWorker() {
            super(null);
        }
        @Override
        public List<FileInfo> createFileManifest(List<Path> paths) {
            return List.of(new FileInfo("test.txt", 100L, "hash"));
        }
    }

    private FakeTrustService trustServiceA;
    private FakeTrustService trustServiceB;
    private FakeSecureSession sessionA;
    private FakeSecureSession sessionB;

    private List<TransferSession> eventsA;
    private List<TransferSession> eventsB;

    private FileTransferEngine engineA;
    private FileTransferEngine engineB;

    private Peer peerBOnA;

    @BeforeEach
    void setUp() {
        trustServiceA = new FakeTrustService();
        trustServiceB = new FakeTrustService();

        eventsA = new CopyOnWriteArrayList<>();
        eventsB = new CopyOnWriteArrayList<>();

        sessionA = new FakeSecureSession("fp-B", "849201");
        sessionB = new FakeSecureSession("fp-A", "849201");
        sessionA.setPeerSession(sessionB);
        sessionB.setPeerSession(sessionA);

        peerBOnA = new Peer(new PeerId("peer-B"), "Device B", "127.0.0.1:9090", false);

        DiscoveryService discoveryA = new DiscoveryService() {
            @Override public void start() {}
            @Override public void stop() {}
            @Override public List<Peer> getDiscoveredPeers() { return List.of(peerBOnA); }
        };
        DiscoveryService discoveryB = new DiscoveryService() {
            @Override public void start() {}
            @Override public void stop() {}
            @Override public List<Peer> getDiscoveredPeers() { return Collections.emptyList(); }
        };

        ConnectionManager connMgrA = new ConnectionManager() {
            @Override public void setIncomingSessionHandler(Consumer<SecureSession> h) {}
            @Override public void listen() {}
            @Override public SecureSession connect(Peer p) { return sessionA; }
            @Override public void closeConnection() {}
        };

        ConnectionManager connMgrB = new ConnectionManager() {
            @Override public void setIncomingSessionHandler(Consumer<SecureSession> h) {}
            @Override public void listen() {}
            @Override public SecureSession connect(Peer p) { return sessionB; }
            @Override public void closeConnection() {}
        };

        StreamWorker streamWorker = new FakeStreamWorker();
        TransferProtocolManager protocolManager = new TransferProtocolManager();

        SessionRepository repoA = new SessionRepository();
        SessionRepository repoB = new SessionRepository();

        TransferEventPublisher pubA = new TransferEventPublisher(eventsA::add);
        TransferEventPublisher pubB = new TransferEventPublisher(eventsB::add);

        engineA = new FileTransferEngine("peer-A", "Device A", discoveryA, connMgrA, streamWorker, protocolManager, repoA, pubA, trustServiceA);
        engineB = new FileTransferEngine("peer-B", "Device B", discoveryB, connMgrB, streamWorker, protocolManager, repoB, pubB, trustServiceB);
    }

    @Test
    void controlMessage_jsonBackwardsCompatibility() throws Exception {
        // 9-argument JSON (legacy)
        String legacyJson = """
            {"type":"TRANSFER_REQUEST","transferId":"uuid-1","fileName":"a.txt","fileSize":10,"files":[],"senderId":"s1","senderDisplayName":"S1","checksum":"c","offset":0}
            """;
        ControlMessage msg = mapper.readValue(legacyJson, ControlMessage.class);
        assertEquals("TRANSFER_REQUEST", msg.type());
        assertEquals("uuid-1", msg.transferId());
        assertFalse(msg.senderTrustsPeer());

        // 10-argument message
        ControlMessage newMsg = new ControlMessage("HANDSHAKE_HELLO", "uuid-2", "b.txt", 20, null, "s2", "S2", null, 0, true);
        String json = mapper.writeValueAsString(newMsg);
        ControlMessage deserialized = mapper.readValue(json, ControlMessage.class);
        assertEquals("HANDSHAKE_HELLO", deserialized.type());
        assertTrue(deserialized.senderTrustsPeer());
    }

    @Test
    void simultaneousPairing_bothPromptsAppearBeforeEitherUserAccepts() throws Exception {
        UUID transferId = UUID.randomUUID();

        // Start B listening for incoming session in a background thread
        engineB.handleIncomingSession(sessionB);

        // Start A initiating transfer in a background thread
        engineA.initiateTransfer(transferId, peerBOnA.id(), List.of(Path.of("test.txt")));

        // Wait until both A and B have published PAIRING state
        TransferSession pairingEventA = awaitState(eventsA, TransferState.PAIRING, 3);
        TransferSession pairingEventB = awaitState(eventsB, TransferState.PAIRING, 3);

        assertNotNull(pairingEventA, "Sender A should publish PAIRING state");
        assertNotNull(pairingEventB, "Receiver B should publish PAIRING state");

        // Verify matching SAS code on both devices
        assertEquals("849201", pairingEventA.sasCode());
        assertEquals("849201", pairingEventB.sasCode());

        // Verify neither side has saved trust yet!
        assertFalse(trustServiceA.isPeerTrusted("peer-B", "fp-B"));
        assertFalse(trustServiceB.isPeerTrusted("peer-A", "fp-A"));
    }

    @Test
    void bothAccept_persistsTrustAndProceeds() throws Exception {
        UUID transferId = UUID.randomUUID();

        engineB.handleIncomingSession(sessionB);
        engineA.initiateTransfer(transferId, peerBOnA.id(), List.of(Path.of("test.txt")));

        awaitState(eventsA, TransferState.PAIRING, 3);
        awaitState(eventsB, TransferState.PAIRING, 3);

        // Both users click accept
        engineA.confirmPairing(transferId, true);
        engineB.confirmPairing(transferId, true);

        // Receiver B should reach OFFERED state for the incoming file transfer
        TransferSession offered = awaitState(eventsB, TransferState.OFFERED, 3);
        assertNotNull(offered, "Receiver should receive TRANSFER_REQUEST and publish OFFERED");

        // Trust must now be saved on both sides!
        assertTrue(trustServiceA.isPeerTrusted("peer-B", "fp-B"), "A must trust B");
        assertTrue(trustServiceB.isPeerTrusted("peer-A", "fp-A"), "B must trust A");
    }

    @Test
    void senderRejects_cancelsBothAndSavesNoTrust() throws Exception {
        UUID transferId = UUID.randomUUID();

        engineB.handleIncomingSession(sessionB);
        engineA.initiateTransfer(transferId, peerBOnA.id(), List.of(Path.of("test.txt")));

        awaitState(eventsA, TransferState.PAIRING, 3);
        awaitState(eventsB, TransferState.PAIRING, 3);

        // Sender A rejects pairing
        engineA.confirmPairing(transferId, false);

        TransferSession cancelledA = awaitState(eventsA, TransferState.CANCELLED, 3);
        TransferSession cancelledB = awaitState(eventsB, TransferState.CANCELLED, 3);

        assertNotNull(cancelledA);
        assertNotNull(cancelledB);

        // No trust saved on either side
        assertFalse(trustServiceA.isPeerTrusted("peer-B", "fp-B"));
        assertFalse(trustServiceB.isPeerTrusted("peer-A", "fp-A"));
    }

    @Test
    void receiverRejects_cancelsBothAndSavesNoTrust() throws Exception {
        UUID transferId = UUID.randomUUID();

        engineB.handleIncomingSession(sessionB);
        engineA.initiateTransfer(transferId, peerBOnA.id(), List.of(Path.of("test.txt")));

        awaitState(eventsA, TransferState.PAIRING, 3);
        awaitState(eventsB, TransferState.PAIRING, 3);

        // Receiver B rejects pairing
        engineB.confirmPairing(transferId, false);

        TransferSession cancelledA = awaitState(eventsA, TransferState.CANCELLED, 3);
        TransferSession cancelledB = awaitState(eventsB, TransferState.CANCELLED, 3);

        assertNotNull(cancelledA);
        assertNotNull(cancelledB);

        assertFalse(trustServiceA.isPeerTrusted("peer-B", "fp-B"));
        assertFalse(trustServiceB.isPeerTrusted("peer-A", "fp-A"));
    }

    @Test
    void asymmetricTrust_entersPairingWhenOneSideTrusts() throws Exception {
        // A already trusts B, but B does not trust A
        trustServiceA.trustPeer("peer-B", "fp-B");

        UUID transferId = UUID.randomUUID();

        engineB.handleIncomingSession(sessionB);
        engineA.initiateTransfer(transferId, peerBOnA.id(), List.of(Path.of("test.txt")));

        // Both MUST still enter pairing because B does not trust A
        TransferSession pairingA = awaitState(eventsA, TransferState.PAIRING, 3);
        TransferSession pairingB = awaitState(eventsB, TransferState.PAIRING, 3);

        assertNotNull(pairingA, "Sender A must enter pairing even if it already trusts B");
        assertNotNull(pairingB, "Receiver B must enter pairing");
    }

    @Test
    void mutualTrust_skipsPairingPrompt() throws Exception {
        // Both already trust each other
        trustServiceA.trustPeer("peer-B", "fp-B");
        trustServiceB.trustPeer("peer-A", "fp-A");

        UUID transferId = UUID.randomUUID();

        engineB.handleIncomingSession(sessionB);
        engineA.initiateTransfer(transferId, peerBOnA.id(), List.of(Path.of("test.txt")));

        // Receiver should directly receive TRANSFER_REQUEST and publish OFFERED
        TransferSession offered = awaitState(eventsB, TransferState.OFFERED, 3);
        assertNotNull(offered, "Receiver should proceed directly to OFFERED");

        // Verify neither A nor B ever published PAIRING state
        boolean aHadPairing = eventsA.stream().anyMatch(e -> e.state() == TransferState.PAIRING);
        boolean bHadPairing = eventsB.stream().anyMatch(e -> e.state() == TransferState.PAIRING);

        assertFalse(aHadPairing, "A should not enter PAIRING");
        assertFalse(bHadPairing, "B should not enter PAIRING");
    }

    private TransferSession awaitState(List<TransferSession> list, TransferState state, int timeoutSeconds) throws InterruptedException {
        long end = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        while (System.currentTimeMillis() < end) {
            for (TransferSession s : list) {
                if (s.state() == state) {
                    return s;
                }
            }
            Thread.sleep(50);
        }
        return null;
    }
}
