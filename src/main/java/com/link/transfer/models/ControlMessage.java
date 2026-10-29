package com.link.transfer.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ControlMessage(
    String type, 
    String transferId, 
    String fileName, 
    long fileSize,
    List<FileInfo> files,
    String senderId,
    String senderDisplayName,
    String checksum,
    long offset,
    boolean senderTrustsPeer
) {
    public ControlMessage(
            String type, 
            String transferId, 
            String fileName, 
            long fileSize,
            List<FileInfo> files, 
            String senderId, 
            String senderDisplayName, 
            String checksum, 
            long offset) {
        this(type, transferId, fileName, fileSize, files, senderId, senderDisplayName, checksum, offset, false);
    }
}
