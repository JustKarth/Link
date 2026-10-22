package com.link.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TransferRepositoryTest {

    private DatabaseManager dbManager;
    private TransferRepository repository;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        Path dbPath = tempDir.resolve("repo_test.sqlite");
        dbManager = new DatabaseManager(dbPath.toAbsolutePath().toString());
        dbManager.initializeDatabase();
        repository = new TransferRepository(dbManager);
    }

    @Test
    void testCreateAndUpdateTransfer() throws SQLException {
        String transferId = UUID.randomUUID().toString();
        
        repository.createTransferRecord(transferId, "INCOMING", "peer-123", "STARTED");
        
        repository.updateTransferState(transferId, "COMPLETED", 1024L);
        
        String query = "SELECT state, completed_bytes FROM transfers WHERE transfer_id = ?";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            
            pstmt.setString(1, transferId);
            ResultSet rs = pstmt.executeQuery();
            
            assertTrue(rs.next());
            assertEquals("COMPLETED", rs.getString("state"));
            assertEquals(1024L, rs.getLong("completed_bytes"));
        }
    }
}