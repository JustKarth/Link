package com.link.domain;

import java.util.UUID;

public record TransferSession(
    UUID transferId,
    TransferDirection direction,
    Peer peer,
    TransferState state,
    TransferProgress progress,
    String failureReason
) {}