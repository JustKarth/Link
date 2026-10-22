package com.link.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {

    @Test
    void testSchemaInitialization(@TempDir Path tempDir) throws SQLException {
        // Use a temporary file database so the schema persists after initialization closes its connection
        Path dbPath = tempDir.resolve("test.sqlite");
        DatabaseManager dbManager = new DatabaseManager(dbPath.toAbsolutePath().toString());
        
        dbManager.initializeDatabase();

        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement()) {
            
            ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='transfers';");
            assertTrue(rs.next(), "Transfers table should exist in database schema");
        }
    }
}