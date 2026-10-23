package com.link.networking;

public interface SecureSession {
    void authenticatePeer();
    void sendMessage(String messagePayload);
    String receiveMessage();
    
    // Add binary support for actual file chunks
    void sendData(byte[] data);
    byte[] receiveData();
    void close();
}