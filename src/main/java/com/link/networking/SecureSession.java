package com.link.networking;


public interface SecureSession {
    void authenticatePeer();
    void sendMessage(String messagePayload);
    String receiveMessage();
}