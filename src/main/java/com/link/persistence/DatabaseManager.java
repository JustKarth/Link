package com.link.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;


public class DatabaseManager {

    private final String dbUrl;

    public DatabaseManager(String dbPath) {
        this.dbUrl = "jdbc:sqlite:" + dbPath;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }

    public void initializeDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            stmt.execute("PRAGMA journal_mode=WAL;");

            String createTransfersTable = """
                CREATE TABLE IF NOT EXISTS transfers (
                    transfer_id TEXT PRIMARY KEY,
                    direction TEXT NOT NULL,
                    peer_id TEXT,
                    state TEXT NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    total_bytes INTEGER,
                    completed_bytes INTEGER,
                    error_code TEXT,
                    error_detail TEXT
                );
            """;

            String createTrustedPeersTable = """
                CREATE TABLE IF NOT EXISTS trusted_peers (
                    peer_id TEXT PRIMARY KEY,
                    display_name TEXT,
                    public_key_fingerprint TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    last_seen TIMESTAMP,
                    revoked INTEGER DEFAULT 0
                );
            """;

            stmt.execute(createTransfersTable);
            stmt.execute(createTrustedPeersTable);

        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize database schema", e);
        }
    }
}