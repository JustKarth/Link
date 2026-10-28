package com.link.networking;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Consumer;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class DefaultConnectionManager implements ConnectionManager {

    private final int port;
    private final PeerTrustService trustService; // <-- 1. Add this field
    private ServerSocket serverSocket;
    private Consumer<SecureSession> incomingSessionHandler;
    private volatile boolean isRunning = false;

    // 2. Update constructor to accept the trust service
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
        isRunning = true;
        new Thread(() -> {
            try {
                // Obtain SSLServerSocketFactory from SslContextFactory
                SSLServerSocketFactory ssf = SslContextFactory.getSSLContext().getServerSocketFactory();
                serverSocket = ssf.createServerSocket(port);

                // Disable hostname verification at the server-socket level so that
                // every accepted connection inherits the setting.  This is required
                // because our self-signed certs use CN=LinkP2P with no SAN — the
                // default HTTPS endpoint-identification check would reject them.
                if (serverSocket instanceof SSLServerSocket sslServerSocket) {
                    sslServerSocket.setNeedClientAuth(true);
                    SSLParameters params = sslServerSocket.getSSLParameters();
                    params.setEndpointIdentificationAlgorithm(null);
                    sslServerSocket.setSSLParameters(params);
                }

                while (isRunning) {
                    Socket clientSocket = serverSocket.accept();

                    if (clientSocket instanceof SSLSocket sslSocket) {
                        sslSocket.setUseClientMode(false);
                        // Require remote client to present its TLS certificate
                        sslSocket.setNeedClientAuth(true);

                        // Disable hostname verification — trust is handled by SAS
                        // fingerprinting in authenticatePeer(), not by certificate CN/SAN.
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
                }
            } catch (Exception e) {
                if (isRunning) {
                    e.printStackTrace();
                }
            }
        }, "Link-NetworkListener").start();
    }

    @Override
    public SecureSession connect(com.link.domain.Peer peer) {
        try {
            String[] parts = peer.candidateAddress().split(":");
            String ip = parts[0];
            int peerPort = parts.length > 1 ? Integer.parseInt(parts[1]) : port;

            // Obtain SSLSocketFactory from SslContextFactory
            SSLSocketFactory ssf = SslContextFactory.getSSLContext().getSocketFactory();
            SSLSocket socket = (SSLSocket) ssf.createSocket(ip, peerPort);
            socket.setUseClientMode(true);

            // ── THIS WAS THE BUG ──
            // Java 9+ defaults endpointIdentificationAlgorithm to "HTTPS" on
            // SSLSockets.  During startHandshake(), Java checks the peer cert's
            // SAN/CN against the hostname (a raw LAN IP like 192.168.x.x).
            // Our self-signed cert has CN=LinkP2P with no SAN extension, so the
            // check fails instantly with an SSLHandshakeException *before any
            // bytes leave the machine* — the receiver never sees the connection.
            //
            // Fix: explicitly disable endpoint identification.  Peer trust is
            // verified at the application layer via SAS fingerprinting.
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

}