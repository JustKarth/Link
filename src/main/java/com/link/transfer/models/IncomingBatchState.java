package com.link.transfer.models;
import com.link.domain.Peer;
import java.nio.file.Path;
import java.util.List;

public record IncomingBatchState(
    List<FileInfo> files, 
    long totalBatchSize, 
    Peer peer, 
    Path destDir
) {}