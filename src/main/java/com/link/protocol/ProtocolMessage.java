package com.link.protocol;

import java.util.UUID;

public record ProtocolMessage(
    int protocolVersion,
    MessageType type,
    UUID transferId, 
    String payload
) {
    public enum MessageType {
        HELLO,
        PAIRING_CHALLENGE,
        PAIRING_CONFIRM,
        TRANSFER_REQUEST,
        TRANSFER_ACCEPT,
        TRANSFER_REJECT,
        TRANSFER_READY,
        PAUSE,
        RESUME,
        CANCEL,
        TRANSFER_COMPLETE,
        ERROR
    }
}