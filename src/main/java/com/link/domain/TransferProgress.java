package com.link.domain;

public record TransferProgress(
    long totalBytes, 
    long completedBytes, 
    long speedBytesPerSecond
) {}