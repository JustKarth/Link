package com.link.domain;

import java.util.List;

public record TransferManifest(
    List<ManifestEntry> entries,
    long totalSizeBytes
) {
    public void validate() {
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("Manifest must contain at least one entry.");
        }
        
        if (entries.size() > 50000) {
            throw new IllegalArgumentException("Manifest exceeds maximum entry limit.");
        }
        
        for (ManifestEntry entry : entries) {
            if (entry.relativePath() == null || entry.relativePath().isBlank()) {
                throw new IllegalArgumentException("Manifest entry path cannot be null or empty.");
            }
            if (entry.relativePath().contains("..")) {
                throw new IllegalArgumentException("Path traversal components (..) are strictly forbidden.");
            }
        }
    }
}