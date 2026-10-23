package com.link.networking;

import com.link.domain.Peer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

public class DefaultConnectionManager implements ConnectionManager {

    private final int port;
    private ServerSocket serverSocket;
    private Consumer<SecureSession> incomingSessionHandler;

    public DefaultConnectionManager(int port) {
        this.port = port;
    }

    @Override
    public void setIncomingSessionHandler(Consumer<SecureSession> handler) {
        this.incomingSessionHandler = handler;
    }

    @Override
    public void listen() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                while (!serverSocket.isClosed()) {
                    Socket clientSocket = serverSocket.accept();
                    SecureSession session = new DefaultSecureSession(clientSocket);
                    
                    if (incomingSessionHandler != null) {
                        incomingSessionHandler.accept(session);
                    }
                }
            } catch (IOException e) {
                // Handle listen failure gracefully if closed intentionally
                if (serverSocket != null && !serverSocket.isClosed()) {
                    e.printStackTrace();
                }
            }
        }, "TCP-Listener-Thread").start();
    }

    @Override
    public SecureSession connect(Peer peer) {
        try {
            String[] parts = peer.candidateAddress().split(":");
            String ip = parts[0];
            int peerPort = Integer.parseInt(parts[1]);
            
            Socket socket = new Socket(ip, peerPort);
            return new DefaultSecureSession(socket);
        } catch (IOException e) {
            throw new RuntimeException("Failed to connect to peer", e);
        }
    }

    @Override
    public void closeConnection() {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
    }
}