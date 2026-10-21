package com.link.networking;

import com.link.domain.Peer;

public interface ConnectionManager {
    void listen();
    SecureSession connect(Peer peer);
    void closeConnection();
}