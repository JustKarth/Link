package com.link.domain;

public record ManifestEntry(
    String relativePath,
    EntryType type,
    long sizeBytes,
    String expectedSha256
) {
    public enum EntryType {
        FILE, DIRECTORY
    }
}