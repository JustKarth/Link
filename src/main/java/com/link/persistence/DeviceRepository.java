package com.link.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists saved (bookmarked) devices to the project's existing SQLite database.
 * Operates on its own {@code saved_devices} table; does not touch {@code transfers}
 * or {@code trusted_peers}.
 */
public class DeviceRepository {

    private final DatabaseManager db;

    public DeviceRepository(DatabaseManager db) {
        this.db = db;
        initTable();
    }

    // ---- Schema -----------------------------------------------------------

    private void initTable() {
        String ddl = """
            CREATE TABLE IF NOT EXISTS saved_devices (
                peer_id        TEXT PRIMARY KEY,
                display_name   TEXT NOT NULL,
                device_type    TEXT NOT NULL DEFAULT 'LAPTOP',
                ip_address     TEXT NOT NULL DEFAULT '',
                favorite       INTEGER NOT NULL DEFAULT 0,
                first_seen_ms  INTEGER NOT NULL,
                last_seen_ms   INTEGER NOT NULL
            );
            """;
        try (Connection c = db.getConnection(); Statement s = c.createStatement()) {
            s.execute(ddl);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create saved_devices table", e);
        }
    }

    // ---- Queries ----------------------------------------------------------

    /** Returns all saved devices, favorites first, then alphabetical. */
    public List<SavedDevice> loadAll() {
        String sql = """
            SELECT peer_id, display_name, device_type, ip_address,
                   favorite, first_seen_ms, last_seen_ms
            FROM saved_devices
            ORDER BY favorite DESC, display_name ASC
            """;
        List<SavedDevice> result = new ArrayList<>();
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(fromRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load saved devices", e);
        }
        return result;
    }

    /** Returns true if a device with the given peerId is already saved. */
    public boolean exists(String peerId) {
        String sql = "SELECT 1 FROM saved_devices WHERE peer_id = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, peerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to check device existence", e);
        }
    }

    // ---- Mutations --------------------------------------------------------

    /**
     * Inserts a new saved device. If a device with the same peerId already exists
     * the row is updated (upsert), keeping the original first_seen_ms.
     */
    public void save(SavedDevice d) {
        String sql = """
            INSERT INTO saved_devices
                (peer_id, display_name, device_type, ip_address, favorite,
                 first_seen_ms, last_seen_ms)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(peer_id) DO UPDATE SET
                display_name = excluded.display_name,
                device_type  = excluded.device_type,
                ip_address   = excluded.ip_address,
                favorite     = excluded.favorite,
                last_seen_ms = excluded.last_seen_ms
            """;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, d.getPeerId());
            ps.setString(2, d.getDisplayName());
            ps.setString(3, d.getDeviceType());
            ps.setString(4, d.getIpAddress());
            ps.setInt(5, d.isFavorite() ? 1 : 0);
            ps.setLong(6, d.getFirstSeenMillis());
            ps.setLong(7, d.getLastSeenMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save device", e);
        }
    }

    public void rename(String peerId, String newName) {
        String sql = "UPDATE saved_devices SET display_name = ? WHERE peer_id = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, newName);
            ps.setString(2, peerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to rename device", e);
        }
    }

    public void setFavorite(String peerId, boolean favorite) {
        String sql = "UPDATE saved_devices SET favorite = ? WHERE peer_id = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, favorite ? 1 : 0);
            ps.setString(2, peerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update favorite", e);
        }
    }

    public void updateLastSeen(String peerId, long lastSeenMs) {
        String sql = "UPDATE saved_devices SET last_seen_ms = ? WHERE peer_id = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, lastSeenMs);
            ps.setString(2, peerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update last seen", e);
        }
    }

    public void delete(String peerId) {
        String sql = "DELETE FROM saved_devices WHERE peer_id = ?";
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, peerId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete saved device", e);
        }
    }

    // ---- Helpers ----------------------------------------------------------

    private static SavedDevice fromRow(ResultSet rs) throws SQLException {
        return new SavedDevice(
                rs.getString("peer_id"),
                rs.getString("display_name"),
                rs.getString("device_type"),
                rs.getString("ip_address"),
                rs.getInt("favorite") == 1,
                rs.getLong("first_seen_ms"),
                rs.getLong("last_seen_ms")
        );
    }
}
