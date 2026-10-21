package com.link.filesystem;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class IntegrityService {

    private static final int BUFFER_SIZE = 8192; //KiB

    //Calc SHA
    public String calculateSHA256(Path filePath) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available on this platform", e);
        }

        try (InputStream fis = Files.newInputStream(filePath);
             BufferedInputStream bis = new BufferedInputStream(fis, BUFFER_SIZE);
             DigestInputStream dis = new DigestInputStream(bis, digest)) {
            
            byte[] buffer = new byte[BUFFER_SIZE];
            while (dis.read(buffer) != -1) {
            }
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    //Match
    public boolean verifyFile(Path filePath, String expectedSha256) throws IOException {
        if (expectedSha256 == null || expectedSha256.isBlank()) {
            return false;
        }
        String actualSha256 = calculateSHA256(filePath);
        return actualSha256.equalsIgnoreCase(expectedSha256.trim());
    }
}