package com.link.application;

import com.link.domain.Peer;
import java.util.List;

public interface DiscoveryService {
    void start();
    void stop();
    List<Peer> getDiscoveredPeers();
}