package com.link.transfer.models;
import java.util.List;

public record ControlMessage(
    String type, 
    String transferId, 
    String fileName, 
    long fileSize,
    List<FileInfo> files,
    String senderId,
    String senderDisplayName,
    String checksum,
    long offset
) {}