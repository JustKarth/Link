package com.link.transfer;

import com.link.filesystem.IntegrityService;
import com.link.networking.SecureSession;
import com.link.transfer.models.FileInfo;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class StreamWorker {
    private final IntegrityService integrityService;

    public interface ProgressCallback {
        void onProgress(long totalBytesTransferred);
    }

    public StreamWorker(IntegrityService integrityService) {
        this.integrityService = integrityService;
    }

    // FIX 1: Added 'throws Exception' here
    public List<FileInfo> createFileManifest(List<Path> paths) throws Exception {
        List<FileInfo> manifest = new ArrayList<>();
        for (Path p : paths) {
            File file = p.toFile();
            if (!file.exists() || !file.isFile()) continue;
            String checksum = integrityService.calculateSHA256(p);
            manifest.add(new FileInfo(file.getName(), file.length(), checksum));
        }
        return manifest;
    }

    public void sendFiles(SecureSession session, List<Path> sourcePaths, long resumeOffset, ProgressCallback callback) throws Exception {
        long overallCompletedBytes = 0;

        for (Path path : sourcePaths) {
            File file = path.toFile();
            if (!file.exists() || !file.isFile()) continue;

            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                if (resumeOffset > overallCompletedBytes) {
                    long skip = Math.min(file.length(), resumeOffset - overallCompletedBytes);
                    raf.seek(skip);
                    overallCompletedBytes += skip;
                }

                byte[] buffer = new byte[8192];
                int read;
                while ((read = raf.read(buffer)) > 0) {
                    if (read == buffer.length) {
                        session.sendData(buffer);
                    } else {
                        byte[] exactChunk = new byte[read];
                        System.arraycopy(buffer, 0, exactChunk, 0, read);
                        session.sendData(exactChunk);
                    }
                    overallCompletedBytes += read;
                    callback.onProgress(overallCompletedBytes);
                }
            }
        }
    }

    public boolean receiveFiles(SecureSession session, List<FileInfo> files, Path destDir, long existingOffset, ProgressCallback callback) throws Exception {
        long overallReceived = existingOffset;

        for (FileInfo fi : files) {
            File tempFile = destDir.resolve(fi.fileName() + ".tmp").toFile();
            long fileAlreadyOnDisk = tempFile.exists() ? tempFile.length() : 0;

            try (FileOutputStream fos = new FileOutputStream(tempFile, true)) {
                long fileReceived = fileAlreadyOnDisk;

                while (fileReceived < fi.fileSize()) {
                    byte[] chunk = session.receiveData();
                    if (chunk.length > 0 && chunk[0] == '{') {
                        String maybeJson = new String(chunk);
                        if (maybeJson.contains("TRANSFER_COMPLETED")) break;
                    }
                    
                    fos.write(chunk);
                    fileReceived += chunk.length;
                    overallReceived += chunk.length;
                    callback.onProgress(overallReceived);
                }
            }
        }
        return true;
    }

    // FIX 2: Added 'throws Exception' here
    public boolean verifyAndCommit(List<FileInfo> files, Path destDir) throws Exception {
        for (FileInfo fi : files) {
            File tmp = destDir.resolve(fi.fileName() + ".tmp").toFile();
            if (!integrityService.verifyFile(tmp.toPath(), fi.checksum())) return false;
        }

        for (FileInfo fi : files) {
            File tmp = destDir.resolve(fi.fileName() + ".tmp").toFile();
            File target = destDir.resolve(fi.fileName()).toFile();
            if (target.exists()) target.delete();
            tmp.renameTo(target);
        }
        return true;
    }

    public void cleanupTempFiles(List<FileInfo> files, Path destDir) {
        if (destDir == null) return;
        for (FileInfo fi : files) {
            File tmp = destDir.resolve(fi.fileName() + ".tmp").toFile();
            try { Files.deleteIfExists(tmp.toPath()); } catch (Exception ignored) {}
        }
    }
}