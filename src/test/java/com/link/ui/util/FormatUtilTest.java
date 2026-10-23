package com.link.ui.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link FormatUtil}. Pure logic — no JavaFX runtime required.
 */
class FormatUtilTest {

    // =========================================================================
    // formatBytes
    // =========================================================================

    @Test void formatBytes_zero()          { assertEquals("0 B",    FormatUtil.formatBytes(0)); }
    @Test void formatBytes_negative()      { assertEquals("0 B",    FormatUtil.formatBytes(-1)); }
    @Test void formatBytes_bytes()         { assertEquals("512 B",  FormatUtil.formatBytes(512)); }
    @Test void formatBytes_exactKb()       { assertEquals("1.0 KB", FormatUtil.formatBytes(1024)); }
    @Test void formatBytes_fractionalKb()  { assertEquals("1.5 KB", FormatUtil.formatBytes(1536)); }
    @Test void formatBytes_megabytes()     { assertTrue(FormatUtil.formatBytes(3_400_000).contains("MB")); }
    @Test void formatBytes_gigabytes()     { assertTrue(FormatUtil.formatBytes(2_000_000_000L).contains("GB")); }

    // =========================================================================
    // formatSpeed
    // =========================================================================

    @Test void formatSpeed_zero()          { assertEquals("—",  FormatUtil.formatSpeed(0)); }
    @Test void formatSpeed_negative()      { assertEquals("—",  FormatUtil.formatSpeed(-100)); }
    @Test void formatSpeed_positive()      { assertTrue(FormatUtil.formatSpeed(1_000_000).contains("/s")); }

    // =========================================================================
    // formatEta
    // =========================================================================

    @Test void formatEta_zero()            { assertEquals("—",       FormatUtil.formatEta(0)); }
    @Test void formatEta_negative()        { assertEquals("—",       FormatUtil.formatEta(-1)); }
    @Test void formatEta_seconds()         { assertEquals("45s left", FormatUtil.formatEta(45)); }
    @Test void formatEta_exactMinute()     { assertEquals("1m 00s left", FormatUtil.formatEta(60)); }
    @Test void formatEta_minutesSeconds()  { assertEquals("1m 04s left", FormatUtil.formatEta(64)); }

    // =========================================================================
    // timeAgo
    // =========================================================================

    @Test
    void timeAgo_justNow() {
        long now = System.currentTimeMillis();
        assertEquals("just now", FormatUtil.timeAgo(now - 10_000, now));
    }

    @Test
    void timeAgo_minutes() {
        long now = System.currentTimeMillis();
        String result = FormatUtil.timeAgo(now - 5 * 60_000, now);
        assertEquals("5m ago", result);
    }

    @Test
    void timeAgo_hours() {
        long now = System.currentTimeMillis();
        String result = FormatUtil.timeAgo(now - 3 * 3_600_000L, now);
        assertEquals("3h ago", result);
    }

    @Test
    void timeAgo_yesterday() {
        long now = System.currentTimeMillis();
        String result = FormatUtil.timeAgo(now - 30 * 3_600_000L, now);
        assertEquals("Yesterday", result);
    }

    // =========================================================================
    // extensionOf
    // =========================================================================

    @Test void extensionOf_pdf()           { assertEquals("pdf",  FormatUtil.extensionOf("report.pdf")); }
    @Test void extensionOf_noExtension()   { assertEquals("",     FormatUtil.extensionOf("README")); }
    @Test void extensionOf_null()          { assertEquals("",     FormatUtil.extensionOf(null)); }
    @Test void extensionOf_trailingDot()   { assertEquals("",     FormatUtil.extensionOf("file.")); }
    @Test void extensionOf_multiDot()      { assertEquals("gz",   FormatUtil.extensionOf("archive.tar.gz")); }

    // =========================================================================
    // categorize
    // =========================================================================

    @Test void categorize_image()    { assertEquals(FormatUtil.FileCategory.IMAGE,    FormatUtil.categorize("photo.png")); }
    @Test void categorize_video()    { assertEquals(FormatUtil.FileCategory.VIDEO,    FormatUtil.categorize("clip.mp4")); }
    @Test void categorize_audio()    { assertEquals(FormatUtil.FileCategory.AUDIO,    FormatUtil.categorize("song.mp3")); }
    @Test void categorize_document() { assertEquals(FormatUtil.FileCategory.DOCUMENT, FormatUtil.categorize("report.pdf")); }
    @Test void categorize_archive()  { assertEquals(FormatUtil.FileCategory.ARCHIVE,  FormatUtil.categorize("files.zip")); }
    @Test void categorize_code()     { assertEquals(FormatUtil.FileCategory.CODE,     FormatUtil.categorize("Main.java")); }
    @Test void categorize_other()    { assertEquals(FormatUtil.FileCategory.OTHER,    FormatUtil.categorize("data.xyz")); }
    @Test void categorize_noExt()    { assertEquals(FormatUtil.FileCategory.OTHER,    FormatUtil.categorize("Makefile")); }

    // =========================================================================
    // typeLabel
    // =========================================================================

    @Test void typeLabel_shortExt()   { assertEquals("PDF",   FormatUtil.typeLabel("doc.pdf")); }
    @Test void typeLabel_noExt()      { assertEquals("FILE",  FormatUtil.typeLabel("Makefile")); }
    @Test void typeLabel_longExt()    {
        // Extension longer than 4 chars falls back to category name
        String label = FormatUtil.typeLabel("style.xhtml");
        assertFalse(label.isBlank());
    }
}
