package com.link.discovery;

import com.link.application.DiscoveryService;
import com.link.domain.Peer;
import com.link.domain.PeerId;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UdpDiscoveryService implements DiscoveryService {

    private static final int DISCOVERY_PORT = 8888;
    private static final String BROADCAST_MESSAGE = "LINK_HELLO";
    
    private final String localPeerId;
    private final String localDisplayName;
    private final int localTcpPort;
    
    private DatagramSocket socket;
    private volatile boolean running = false;
    
    // Thread-safe registry of discovered peers
    private final Map<String, Peer> peerRegistry = new ConcurrentHashMap<>();

    public UdpDiscoveryService(String localPeerId, String localDisplayName, int localTcpPort) {
        this.localPeerId = localPeerId;
        this.localDisplayName = localDisplayName;
        this.localTcpPort = localTcpPort;
    }

    @Override
    public void start() {
        if (running) return;
        running = true;
        
        try {
            socket = new DatagramSocket(DISCOVERY_PORT);
            socket.setBroadcast(true);
        } catch (SocketException e) {
            throw new RuntimeException("Failed to bind UDP discovery port", e);
        }

        new Thread(this::listenForPeers, "Discovery-Listener").start();
        
        new Thread(this::broadcastPresence, "Discovery-Broadcaster").start();
    }

    @Override
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    @Override
    public List<Peer> getDiscoveredPeers() {
        return new ArrayList<>(peerRegistry.values());
    }

    private void listenForPeers() {
        byte[] buffer = new byte[256];
        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                
                String message = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                String senderIp = packet.getAddress().getHostAddress();
                
                //format: LINK_HELLO|PeerId|DisplayName|TcpPort
                if (message.startsWith(BROADCAST_MESSAGE)) {
                    parseAndRegisterPeer(message, senderIp);
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error receiving discovery packet: " + e.getMessage());
                }
            }
        }
    }

    private void broadcastPresence() {
        String payload = String.format("%s|%s|%s|%d", 
                BROADCAST_MESSAGE, localPeerId, localDisplayName, localTcpPort);
        byte[] buffer = payload.getBytes(StandardCharsets.UTF_8);

        while (running) {
            try {
                DatagramPacket packet = new DatagramPacket(
                        buffer, buffer.length, 
                        InetAddress.getByName("255.255.255.255"), 
                        DISCOVERY_PORT
                );
                socket.send(packet);
                
                Thread.sleep(3000);
            } catch (IOException | InterruptedException e) {
                if (running) {
                    System.err.println("Error broadcasting presence: " + e.getMessage());
                }
            }
        }
    }

    private void parseAndRegisterPeer(String message, String senderIp) {
        String[] parts = message.split("\\|");
        if (parts.length == 4) {
            String peerIdVal = parts[1];
            
            if (peerIdVal.equals(this.localPeerId)) return;
            
            String displayName = parts[2];
            String tcpPort = parts[3];
            
            PeerId peerId = new PeerId(peerIdVal);
            String candidateAddress = senderIp + ":" + tcpPort;
            
            Peer peer = new Peer(peerId, displayName, candidateAddress, false);
            peerRegistry.put(peerIdVal, peer);
        }
    }
}