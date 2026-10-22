package com.link.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class TransferRepository {

    private final DatabaseManager dbManager;

    public TransferRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void createTransferRecord(String transferId, String direction, String peerId, String initialState) {
        String sql = "INSERT INTO transfers (transfer_id, direction, peer_id, state) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, transferId);
            pstmt.setString(2, direction);
            pstmt.setString(3, peerId);
            pstmt.setString(4, initialState);
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create transfer record", e);
        }
    }

    public void updateTransferState(String transferId, String newState, long completedBytes) {
        String sql = "UPDATE transfers SET state = ?, completed_bytes = ?, updated_at = CURRENT_TIMESTAMP WHERE transfer_id = ?";
        
        try (Connection conn = dbManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, newState);
            pstmt.setLong(2, completedBytes);
            pstmt.setString(3, transferId);
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update transfer state", e);
        }
    }
}