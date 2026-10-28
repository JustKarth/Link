package com.link.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class TrustRepository {

    private final DatabaseManager dbManager;

    public TrustRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void saveTrust(String peerId, String fingerprint) {
        String sql = """
            INSERT INTO trusted_peers (
                peer_id,
                public_key_fingerprint,
                created_at,
                revoked
            )
            VALUES (?, ?, CURRENT_TIMESTAMP, 0)
            ON CONFLICT(peer_id) DO UPDATE SET
                public_key_fingerprint = excluded.public_key_fingerprint,
                created_at = CURRENT_TIMESTAMP,
                revoked = 0
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, peerId);
            pstmt.setString(2, fingerprint);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to save peer trust", e);
        }
    }

    public String getTrustedFingerprint(String peerId) {
        String sql = """
            SELECT public_key_fingerprint
            FROM trusted_peers
            WHERE peer_id = ?
              AND revoked = 0
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, peerId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("public_key_fingerprint");
                }
            }

        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch peer trust", e);
        }

        return null;
    }

    public void revokeTrust(String peerId) {
        String sql = """
            UPDATE trusted_peers
            SET revoked = 1
            WHERE peer_id = ?
            """;

        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, peerId);
            pstmt.executeUpdate();

        } catch (SQLException e) {
            throw new RuntimeException("Failed to revoke peer trust", e);
        }
    }
}