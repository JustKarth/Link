package com.link.networking;

import com.link.persistence.TrustRepository;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.util.Base64;

public class DefaultPeerTrustService implements PeerTrustService {

    private final TrustRepository trustRepository;

    public DefaultPeerTrustService(TrustRepository trustRepository) {
        this.trustRepository = trustRepository;
    }

    @Override
    public String computeFingerprint(Certificate cert) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(cert.getEncoded());
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute cert fingerprint", e);
        }
    }

    @Override
    public String generateSASCode(String peerFingerprint, String localFingerprint) {
        try {
            String combined = peerFingerprint.compareTo(localFingerprint) < 0 
                ? peerFingerprint + localFingerprint 
                : localFingerprint + peerFingerprint;
                
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes());
            
            int numericHash = Math.abs(((hash[0] & 0xFF) << 24) |
                                       ((hash[1] & 0xFF) << 16) |
                                       ((hash[2] & 0xFF) << 8)  |
                                       (hash[3] & 0xFF));
            return String.format("%06d", numericHash % 1_000_000);
        } catch (Exception e) {
            throw new RuntimeException("Failed to derive SAS code", e);
        }
    }

    @Override
    public boolean isPeerTrusted(String peerId, String fingerprint) {
        String existing = trustRepository.getTrustedFingerprint(peerId);
        if (existing == null) return false;
        if (!existing.equals(fingerprint)) {
            throw new SecurityException("POSSIBLE MITM ATTACK: Peer identity fingerprint mismatch!");
        }
        return true;
    }

    @Override
    public void trustPeer(String peerId, String fingerprint) {
        trustRepository.saveTrust(peerId, fingerprint);
    }

    @Override
    public void revokeTrust(String peerId) {
        trustRepository.revokeTrust(peerId);
    }
}