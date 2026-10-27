package com.link.networking;

import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Base64;

public interface PeerTrustService {
    String computeFingerprint(Certificate cert);
    String generateSASCode(String peerFingerprint, String localFingerprint);
    boolean isPeerTrusted(String peerId, String fingerprint);
    void trustPeer(String peerId, String fingerprint);
    void revokeTrust(String peerId);
}