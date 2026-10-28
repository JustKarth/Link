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
        // ── Synchronous bind ──
        // The server socket is created and bound on the calling thread so that
        // by the time listen() returns, the port is guaranteed to be open.
        // This eliminates the race where UDP discovery advertises the service
        // before the TCP listener is ready.
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

        // ── Asynchronous accept loop ──
        isRunning = true;
        Thread acceptThread = new Thread(() -> {
            while (isRunning) {
                Socket clientSocket = null;
                try {
                    clientSocket = serverSocket.accept();

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
                    // A single bad peer (network blip, malformed handshake, auth
                    // failure) must not kill the listener.  Log and continue.
                    if (isRunning) {
                        LOG.log(Level.WARNING, "Incoming connection failed, continuing to listen", e);
                    }
                    // Clean up the failed socket so we don't leak file descriptors.
                    if (clientSocket != null) {
                        try { clientSocket.close(); } catch (IOException ignored) {}
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
            String[] parts = peer.candidateAddress().split(":");
            String ip = parts[0];
            int peerPort = parts.length > 1 ? Integer.parseInt(parts[1]) : port;

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

}