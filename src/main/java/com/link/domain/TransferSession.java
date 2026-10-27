package com.link.domain;

import java.util.UUID;

public record TransferSession(
    UUID transferId,
    String fileName,
    TransferDirection direction,
    Peer peer,
    TransferState state,
    TransferProgress progress,
    String failureReason,
    String sasCode
) {}