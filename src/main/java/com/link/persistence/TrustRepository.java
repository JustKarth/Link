package com.link.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class TrustRepository {

    private final DatabaseManager dbManager;

    public TrustRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        createTableIfNotExists();
    }

    private void createTableIfNotExists() {
        String sql = """
            CREATE TABLE IF NOT EXISTS trusted_peers (
                peer_id TEXT PRIMARY KEY,
                fingerprint TEXT NOT NULL,
                trusted_at INTEGER NOT NULL
            );
        """;
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize trusted_peers table", e);
        }
    }

    public void saveTrust(String peerId, String fingerprint) {
        // UPSERT syntax for SQLite (inserts new, or updates existing fingerprint if peer re-pairs)
        String sql = "INSERT OR REPLACE INTO trusted_peers (peer_id, fingerprint, trusted_at) VALUES (?, ?, ?)";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, peerId);
            pstmt.setString(2, fingerprint);
            pstmt.setLong(3, System.currentTimeMillis());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save peer trust", e);
        }
    }

    public String getTrustedFingerprint(String peerId) {
        String sql = "SELECT fingerprint FROM trusted_peers WHERE peer_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, peerId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("fingerprint");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to fetch peer trust", e);
        }
        return null;
    }

    public void revokeTrust(String peerId) {
        String sql = "DELETE FROM trusted_peers WHERE peer_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, peerId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to revoke peer trust", e);
        }
    }
}