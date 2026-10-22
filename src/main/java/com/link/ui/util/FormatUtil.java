package com.link.ui.util;

/**
 * Small, dependency-free formatting helpers used across the views:
 * human-readable byte sizes, transfer speeds, ETAs, relative timestamps, and
 * file-type categorisation for icon tinting.
 *
 * <p>Pure logic only, so it is unit-testable without a JavaFX runtime.
 */
public final class FormatUtil {

    private FormatUtil() {}

    private static final String[] SIZE_UNITS = {"B", "KB", "MB", "GB", "TB"};

    /** 1536 -> "1.5 KB"; 0 -> "0 B". */
    public static String formatBytes(long bytes) {
        if (bytes < 0) return "0 B";
        if (bytes < 1024) return bytes + " B";
        double v = bytes;
        int unit = 0;
        while (v >= 1024 && unit < SIZE_UNITS.length - 1) {
            v /= 1024;
            unit++;
        }
        // One decimal for KB+, none for raw bytes.
        return String.format("%.1f %s", v, SIZE_UNITS[unit]);
    }

    /** 3_400_000 -> "3.4 MB/s"; 0 -> "—". */
    public static String formatSpeed(long bytesPerSecond) {
        if (bytesPerSecond <= 0) return "—";
        return formatBytes(bytesPerSecond) + "/s";
    }

    /** Seconds remaining -> "12s left", "1m 04s left", "—". */
    public static String formatEta(long secondsRemaining) {
        if (secondsRemaining <= 0) return "—";
        if (secondsRemaining < 60) return secondsRemaining + "s left";
        long m = secondsRemaining / 60;
        long s = secondsRemaining % 60;
        return String.format("%dm %02ds left", m, s);
    }

    /**
     * Compact, relative "time ago" label: "just now", "5m ago", "3h ago",
     * "Yesterday", "Mon", "23 Sep".
     */
    public static String timeAgo(long millis, long nowMillis) {
        long diff = Math.max(0, nowMillis - millis);
        long sec = diff / 1000;
        if (sec < 45) return "just now";
        long min = sec / 60;
        if (min < 60) return min + "m ago";
        long hours = min / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days == 1) return "Yesterday";
        if (days < 7) {
            return java.time.Instant.ofEpochMilli(millis)
                    .atZone(java.time.ZoneId.systemDefault())
                    .getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault());
        }
        java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneId.systemDefault());
        return z.getDayOfMonth() + " " + z.getMonth().getDisplayName(
                java.time.format.TextStyle.SHORT, java.util.Locale.getDefault());
    }

    /** Full timestamp like "21 Oct, 14:32". */
    public static String fullTimestamp(long millis) {
        java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneId.systemDefault());
        return z.getDayOfMonth() + " "
                + z.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
                + ", " + String.format("%02d:%02d", z.getHour(), z.getMinute());
    }

    // ---- File-type categorisation -----------------------------------------

    public enum FileCategory {
        IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, CODE, OTHER
    }

    public static FileCategory categorize(String fileNameOrExtension) {
        String ext = extensionOf(fileNameOrExtension).toLowerCase();
        return switch (ext) {
            case "png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "heic" -> FileCategory.IMAGE;
            case "mp4", "mov", "avi", "mkv", "webm" -> FileCategory.VIDEO;
            case "mp3", "wav", "flac", "aac", "ogg", "m4a" -> FileCategory.AUDIO;
            case "pdf", "doc", "docx", "txt", "md", "rtf", "pages", "xls", "xlsx", "ppt", "pptx", "csv" -> FileCategory.DOCUMENT;
            case "zip", "rar", "7z", "tar", "gz", "iso", "dmg" -> FileCategory.ARCHIVE;
            case "java", "kt", "py", "js", "ts", "json", "xml", "html", "css", "c", "cpp", "go", "rs", "sh", "yml", "yaml" -> FileCategory.CODE;
            default -> FileCategory.OTHER;
        };
    }

    public static String extensionOf(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1);
    }

    /** Short, uppercase label for a file-type chip, e.g. "PDF", "IMAGE". */
    public static String typeLabel(String fileName) {
        String ext = extensionOf(fileName);
        if (ext.isBlank()) return "FILE";
        if (ext.length() <= 4) return ext.toUpperCase();
        return categorize(fileName).name();
    }
}
