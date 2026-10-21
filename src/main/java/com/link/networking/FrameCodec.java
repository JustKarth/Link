package com.link.networking;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class FrameCodec {

    private static final int MAX_FRAME_SIZE = 1024 * 1024; //1MiB

    public byte[] readFrame(DataInputStream in) throws IOException {
        int length = in.readInt();

        if (length <= 0 || length > MAX_FRAME_SIZE) {
            throw new IOException("Invalid frame length received: " + length + ". Must be between 1 and " + MAX_FRAME_SIZE + " bytes.[cite: 15, 16]");
        }

        byte[] payload = new byte[length];
        in.readFully(payload);
        return payload;
    }

    public void writeFrame(DataOutputStream out, byte[] payload) throws IOException {
        if (payload == null || payload.length == 0) {
            throw new IllegalArgumentException("Payload cannot be null or empty.");
        }
        if (payload.length > MAX_FRAME_SIZE) {
            throw new IllegalArgumentException("Payload exceeds maximum frame size.[cite: 15, 16]");
        }

        out.writeInt(payload.length);
        out.write(payload);
        out.flush();
    }
}