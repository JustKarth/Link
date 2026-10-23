package com.link.networking;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class DefaultSecureSession implements SecureSession {

    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final FrameCodec frameCodec;

    public DefaultSecureSession(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(socket.getInputStream());
        this.out = new DataOutputStream(socket.getOutputStream());
        this.frameCodec = new FrameCodec();
    }

    @Override
    public void authenticatePeer() {
        // TLS handshake and PIN validation will be integrated here
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