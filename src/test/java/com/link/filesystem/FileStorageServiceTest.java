package com.link.filesystem;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FileStorageServiceTest {

    private final FileStorageService storageService = new FileStorageService();

    @Test
    void testPathTraversalRejected(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class, () -> {
            storageService.validateDestination(tempDir, "../unsafe.txt");
        });
    }

    @Test
    void testStagingAndFinalization(@TempDir Path tempDir) throws IOException {
        Path targetFile = tempDir.resolve("test.txt");
        UUID transferId = UUID.randomUUID();

        // Create staging file
        Path stagingFile = storageService.createStagingFile(targetFile, transferId);
        assertTrue(Files.exists(stagingFile));

        // Write sample data
        Files.writeString(stagingFile, "Hello Link");

        // Finalize file
        Path finalized = storageService.finalizeFile(stagingFile, targetFile);
        assertTrue(Files.exists(finalized));
        assertFalse(Files.exists(stagingFile));
        assertEquals("Hello Link", Files.readString(finalized));
    }
}