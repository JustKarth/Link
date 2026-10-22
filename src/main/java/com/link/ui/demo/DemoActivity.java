package com.link.ui.demo;

/**
 * One entry in the activity history. Each completed or failed demo transfer
 * produces exactly one activity record. Immutable record so it is safe to
 * hand straight to the UI.
 */
public record DemoActivity(
        String id,
        String fileName,
        long fileSize,
        String fileExtension,
        TransferDirection direction,
        String deviceName,
        boolean completed,        // true = completed, false = failed
        long timestampMillis
) {
}
