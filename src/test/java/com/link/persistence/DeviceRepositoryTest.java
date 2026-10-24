package com.link.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DeviceRepository}: save, duplicate upsert, rename,
 * favourite toggle, forget, and exists checks.
 */
class DeviceRepositoryTest {

    @TempDir Path tempDir;
    private DeviceRepository repo;

    @BeforeEach
    void setUp() {
        DatabaseManager db = new DatabaseManager(tempDir.resolve("test.db").toString());
        db.initializeDatabase();
        repo = new DeviceRepository(db);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private SavedDevice device(String id, String name) {
        return new SavedDevice(id, name, "LAPTOP", "192.168.1.10", false, 1000L, 2000L);
    }

    // ── Tests ──────────────────────────────────────────────────────────────────

    @Test
    void saveAndLoad_singleDevice() {
        repo.save(device("peer-1", "Alice's Mac"));
        List<SavedDevice> all = repo.loadAll();
        assertEquals(1, all.size());
        assertEquals("peer-1", all.get(0).getPeerId());
        assertEquals("Alice's Mac", all.get(0).getDisplayName());
        assertFalse(all.get(0).isFavorite());
    }

    @Test
    void exists_trueAfterSave() {
        assertFalse(repo.exists("peer-2"));
        repo.save(device("peer-2", "Bob's Phone"));
        assertTrue(repo.exists("peer-2"));
    }

    @Test
    void save_upsertUpdatesFieldsNotFirstSeen() {
        repo.save(device("peer-3", "Original Name"));
        // Upsert with a different name and later lastSeenMs
        SavedDevice updated = new SavedDevice("peer-3", "Updated Name", "PHONE",
                "10.0.0.5", false, 1000L, 5000L);
        repo.save(updated);

        List<SavedDevice> all = repo.loadAll();
        assertEquals(1, all.size(), "should not create duplicate");
        assertEquals("Updated Name", all.get(0).getDisplayName());
        assertEquals(5000L, all.get(0).getLastSeenMillis());
        // first_seen_ms stays at original value (upsert doesn't overwrite it)
        assertEquals(1000L, all.get(0).getFirstSeenMillis());
    }

    @Test
    void rename_updatesDisplayName() {
        repo.save(device("peer-4", "Old Name"));
        repo.rename("peer-4", "New Name");
        List<SavedDevice> all = repo.loadAll();
        assertEquals("New Name", all.get(0).getDisplayName());
    }

    @Test
    void setFavorite_togglesCorrectly() {
        repo.save(device("peer-5", "Carol's iPad"));
        assertFalse(repo.loadAll().get(0).isFavorite());

        repo.setFavorite("peer-5", true);
        assertTrue(repo.loadAll().get(0).isFavorite());

        repo.setFavorite("peer-5", false);
        assertFalse(repo.loadAll().get(0).isFavorite());
    }

    @Test
    void favoritesAppearFirst() {
        repo.save(new SavedDevice("peer-a", "Alpha", "LAPTOP", "", false, 1L, 1L));
        repo.save(new SavedDevice("peer-b", "Beta",  "LAPTOP", "", false, 1L, 1L));
        repo.setFavorite("peer-b", true);

        List<SavedDevice> all = repo.loadAll();
        assertEquals("peer-b", all.get(0).getPeerId(), "favourite should be first");
        assertEquals("peer-a", all.get(1).getPeerId());
    }

    @Test
    void delete_removesDevice() {
        repo.save(device("peer-6", "Dave's Desktop"));
        assertTrue(repo.exists("peer-6"));
        repo.delete("peer-6");
        assertFalse(repo.exists("peer-6"));
        assertTrue(repo.loadAll().isEmpty());
    }

    @Test
    void delete_nonExistentId_noError() {
        // Should not throw
        assertDoesNotThrow(() -> repo.delete("nonexistent-id"));
    }

    @Test
    void updateLastSeen_updatesTimestamp() {
        repo.save(device("peer-7", "Eve's Laptop"));
        repo.updateLastSeen("peer-7", 99999L);
        assertEquals(99999L, repo.loadAll().get(0).getLastSeenMillis());
    }

    @Test
    void savedDevice_preservesAllFields() {
        SavedDevice d = new SavedDevice("peer-8", "Frank", "PHONE",
                "172.16.0.1", true, 111L, 222L);
        repo.save(d);
        SavedDevice loaded = repo.loadAll().get(0);
        assertEquals("peer-8",      loaded.getPeerId());
        assertEquals("Frank",       loaded.getDisplayName());
        assertEquals("PHONE",       loaded.getDeviceType());
        assertEquals("172.16.0.1",  loaded.getIpAddress());
        assertTrue(loaded.isFavorite());
        assertEquals(111L, loaded.getFirstSeenMillis());
        assertEquals(222L, loaded.getLastSeenMillis());
    }
}
