package com.link.transfer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;
import java.util.function.Supplier;


public class FileTransferEngine {

    private static final int CHUNK_SIZE = 8192 * 4; //KiB

    /**
     * Streams bytes from the source to the destination in bounded chunks.
     *
     * @param source         The input stream (file for sending, network for receiving).
     * @param destination    The output stream (network for sending, file for receiving).
     * @param expectedSize   The total expected size for progress calculations.
     * @param isCancelled    A supplier that returns true if the transfer should abort.
     * @param progressCb     Callback to report bytes transferred.
     * @throws IOException   If a stream read/write fails.
     */
    public void streamChunks(
            InputStream source, 
            OutputStream destination, 
            long expectedSize, 
            Supplier<Boolean> isCancelled, 
            Consumer<Long> progressCb) throws IOException {
        
        byte[] buffer = new byte[CHUNK_SIZE];
        int bytesRead;
        long totalBytesTransferred = 0;

        while ((bytesRead = source.read(buffer)) != -1) {
            if (isCancelled.get()) {
                throw new IOException("Transfer cancelled by user or coordinator.[cite: 19]");
            }

            destination.write(buffer, 0, bytesRead);
            totalBytesTransferred += bytesRead;

            if (progressCb != null) {
                progressCb.accept(totalBytesTransferred);
            }
        }
        
        destination.flush();
    }
}