package com.link.networking;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class DefaultConnectionManager implements ConnectionManager {

    private static final Logger LOG = Logger.getLogger(DefaultConnectionManager.class.getName());

    private final int port;
    private final PeerTrustService trustService;
    private volatile ServerSocket serverSocket;
    private Consumer<SecureSession> incomingSessionHandler;
    private volatile boolean isRunning = false;

    public DefaultConnectionManager(int port, PeerTrustService trustService) {
        this.port = port;
        this.trustService = trustService;
    }

    @Override
    public void setIncomingSessionHandler(Consumer<SecureSession> handler) {
        this.incomingSessionHandler = handler;
    }

    @Override
    public void listen() {
        try {
            SSLServerSocketFactory ssf = SslContextFactory.getSSLContext().getServerSocketFactory();
            serverSocket = ssf.createServerSocket(port);

            if (serverSocket instanceof SSLServerSocket sslServerSocket) {
                sslServerSocket.setNeedClientAuth(true);
                SSLParameters params = sslServerSocket.getSSLParameters();
                params.setEndpointIdentificationAlgorithm(null);
                sslServerSocket.setSSLParameters(params);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to bind TLS server socket on port " + port, e);
        }

        isRunning = true;
        Thread acceptThread = new Thread(() -> {
            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();

                    // Offload handshake and authentication to a separate thread to prevent blocking the listener
                    new Thread(() -> {
                        try {
                            if (clientSocket instanceof SSLSocket sslSocket) {
                                sslSocket.setUseClientMode(false);
                                sslSocket.setNeedClientAuth(true);

                                SSLParameters params = sslSocket.getSSLParameters();
                                params.setEndpointIdentificationAlgorithm(null);
                                sslSocket.setSSLParameters(params);

                                sslSocket.startHandshake();
                            }

                            DefaultSecureSession session = new DefaultSecureSession(clientSocket, trustService);
                            session.authenticatePeer();

                            if (incomingSessionHandler != null) {
                                incomingSessionHandler.accept(session);
                            }

                        } catch (Exception e) {
                            if (isRunning) {
                                LOG.log(Level.WARNING, "Incoming connection failed during handshake, dropping peer", e);
                            }
                            try { clientSocket.close(); } catch (IOException ignored) {}
                        }
                    }, "Link-IncomingHandshake").start();

                } catch (Exception e) {
                    if (isRunning) {
                        LOG.log(Level.WARNING, "Socket accept failed, continuing to listen", e);
                    }
                }
            }
        }, "Link-NetworkListener");
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    @Override
    public SecureSession connect(com.link.domain.Peer peer) {
        try {
            String address = peer.candidateAddress();
            String ip;
            int peerPort;

            if (address.startsWith("[")) {
                int closeBracket = address.indexOf(']');
                if (closeBracket == -1) {
                    throw new IllegalArgumentException("Malformed IPv6 address (missing ']'): " + address);
                }
                ip = address.substring(1, closeBracket); 
                if (closeBracket + 1 < address.length() && address.charAt(closeBracket + 1) == ':') {
                    peerPort = Integer.parseInt(address.substring(closeBracket + 2));
                } else {
                    peerPort = port; 
                }
            } else {
                long colonCount = address.chars().filter(c -> c == ':').count();
                if (colonCount > 1) {
                    int lastColon = address.lastIndexOf(':');
                    String afterColon = address.substring(lastColon + 1);
                    if (!afterColon.isEmpty() && afterColon.chars().allMatch(Character::isDigit)) {
                        ip = address.substring(0, lastColon);
                        peerPort = Integer.parseInt(afterColon);
                    } else {
                        ip = address;
                        peerPort = port;
                    }
                } else {
                    int lastColon = address.lastIndexOf(':');
                    if (lastColon != -1) {
                        ip = address.substring(0, lastColon);
                        peerPort = Integer.parseInt(address.substring(lastColon + 1));
                    } else {
                        ip = address;
                        peerPort = port;
                    }
                }
            }

            SSLSocketFactory ssf = SslContextFactory.getSSLContext().getSocketFactory();
            SSLSocket socket = (SSLSocket) ssf.createSocket(ip, peerPort);
            socket.setUseClientMode(true);

            SSLParameters params = socket.getSSLParameters();
            params.setEndpointIdentificationAlgorithm(null);
            socket.setSSLParameters(params);

            socket.startHandshake();

            DefaultSecureSession session = new DefaultSecureSession(socket, trustService);
            session.authenticatePeer();
            return session;

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to connect to peer: " + peer.displayName(), e);
        }
    }

    @Override
    public void closeConnection() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}
    }
}