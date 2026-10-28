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

    public void sendFiles(SecureSession session, List<Path> sourcePaths, long resumeOffset,
                          SessionRepository.PauseHandle pauseHandle,
                          ProgressCallback callback) throws Exception {
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
                    // Check pause flag between every chunk. If paused, block here
                    // until resumeTransfer() releases the latch. The sender thread
                    // simply stops writing; the receiver blocks on receiveData()
                    // until chunks start flowing again.
                    if (pauseHandle.isPaused()) {
                        pauseHandle.awaitResume();
                    }

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
                long remaining = fi.fileSize() - fileReceived;

                while (remaining > 0) {
                    byte[] chunk = session.receiveData();
                    // Guard: never write more than the declared file size
                    int toWrite = (int) Math.min(chunk.length, remaining);
                    fos.write(chunk, 0, toWrite);
                    fileReceived += toWrite;
                    overallReceived += toWrite;
                    remaining -= toWrite;
                    callback.onProgress(overallReceived);
                }
            }
        }
        // Read the TRANSFER_COMPLETED control frame that the sender sends after
        // streaming all file data. This keeps control messages on their own
        // dedicated receive path and avoids the previous JSON-sniffing heuristic
        // that could misidentify binary data starting with '{' as a control frame.
        try {
            session.receiveMessage(); // consumes the TRANSFER_COMPLETED frame
        } catch (Exception ignored) {
            // If the session closes before we read the completion frame that is
            // still fine — all bytes were already written and verified separately.
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