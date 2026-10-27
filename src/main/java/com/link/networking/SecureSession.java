package com.link.networking;

public interface SecureSession {
    void authenticatePeer();
    
    String getRemoteFingerprint();
    String getSasPairingCode();
    
    void sendMessage(String messagePayload);
    String receiveMessage();
    
    void sendData(byte[] data);
    byte[] receiveData();
    void close();
}