package com.link.networking;

import com.link.domain.Peer;
import java.util.function.Consumer;

public interface ConnectionManager {
    void setIncomingSessionHandler(Consumer<SecureSession> handler);
    void listen();
    SecureSession connect(Peer peer);
    void closeConnection();
}