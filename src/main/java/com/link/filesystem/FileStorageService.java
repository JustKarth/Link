package com.link.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public class FileStorageService {

    private static final String STAGING_SUFFIX = ".linktmp";

    public Path validateDestination(Path baseDestinationDir, String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Relative path cannot be null or blank.");
        }
        if (relativePath.contains("..")) {
            throw new IllegalArgumentException("Path traversal (..) detected in relative path: " + relativePath);
        }

        Path normalizedBase = baseDestinationDir.toAbsolutePath().normalize();
        Path targetPath = normalizedBase.resolve(relativePath).normalize();

        if (!targetPath.startsWith(normalizedBase)) {
            throw new IllegalArgumentException("Target path attempts to escape destination root directory.");
        }

        return targetPath;
    }

    
    public Path createStagingFile(Path targetPath, UUID transferId) throws IOException {
        Path parent = targetPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        String stagingName = targetPath.getFileName().toString() + "." + transferId.toString().substring(0, 8) + STAGING_SUFFIX;
        Path stagingPath = parent != null ? parent.resolve(stagingName) : Path.of(stagingName);

        // Delete pre existing trash
        Files.deleteIfExists(stagingPath);
        return Files.createFile(stagingPath);
    }

    public Path finalizeFile(Path stagingPath, Path targetPath) throws IOException {
        if (!Files.exists(stagingPath)) {
            throw new IOException("Staging file does not exist: " + stagingPath);
        }

        try {
            return Files.move(stagingPath, targetPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            return Files.move(stagingPath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void cleanupStagingFile(Path stagingPath) {
        if (stagingPath != null) {
            try {
                Files.deleteIfExists(stagingPath);
            } catch (IOException ignored) {
            }
        }
    }
}