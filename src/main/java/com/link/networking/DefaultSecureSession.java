package com.link.networking;

import javax.net.ssl.SSLSocket;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;

public class DefaultSecureSession implements SecureSession {

   private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final FrameCodec frameCodec;
    private final PeerTrustService trustService;
    private String remoteFingerprint;
    private String sasPairingCode;

    // Update constructor to require PeerTrustService
    public DefaultSecureSession(Socket socket, PeerTrustService trustService) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        this.frameCodec = new FrameCodec();
        this.trustService = trustService;
    }

    @Override
    public void authenticatePeer() {
        if (socket instanceof SSLSocket sslSocket) {
            try {
                var session = sslSocket.getSession();
                Certificate[] certs = session.getPeerCertificates();
                if (certs.length > 0) {
                    this.remoteFingerprint = trustService.computeFingerprint(certs[0]);
                    
                    String localFingerprint = trustService.computeFingerprint(SslContextFactory.getLocalCertificate());
                    this.sasPairingCode = trustService.generateSASCode(remoteFingerprint, localFingerprint);
                }
            } catch (Exception e) {
                throw new SecurityException("TLS peer authentication failed", e);
            }
        }
    }

    public String getRemoteFingerprint() {
        return remoteFingerprint;
    }

    public String getSasPairingCode() {
        return sasPairingCode;
    }

    @Override
    public void sendMessage(String messagePayload) {
        try {
            byte[] payloadBytes = messagePayload.getBytes(StandardCharsets.UTF_8);
            frameCodec.writeFrame(out, payloadBytes);
        } catch (IOException e) {
            throw new RuntimeException("Failed to send message", e);
        }
    }

    @Override
    public String receiveMessage() {
        try {
            byte[] payloadBytes = frameCodec.readFrame(in);
            return new String(payloadBytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to receive message", e);
        }
    }

    @Override
    public void sendData(byte[] data) {
        try {
            frameCodec.writeFrame(out, data);
        } catch (IOException e) {
            throw new RuntimeException("Failed to send data", e);
        }
    }

    @Override
    public byte[] receiveData() {
        try {
            return frameCodec.readFrame(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to receive data", e);
        }
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}