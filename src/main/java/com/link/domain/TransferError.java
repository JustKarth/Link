package com.link.domain;

public record TransferError(ErrorCode code, String safeDiagnosticDetail) {
    public enum ErrorCode {
        CONNECTION_FAILED,
        CONNECTION_TIMEOUT,
        PROTOCOL_VERSION_UNSUPPORTED,
        INVALID_MESSAGE,
        AUTHENTICATION_FAILED,
        PAIRING_REQUIRED,
        TRANSFER_REJECTED,
        TRANSFER_CANCELLED,
        DESTINATION_NOT_WRITABLE,
        INSUFFICIENT_SPACE,
        PATH_VALIDATION_FAILED,
        CHECKSUM_MISMATCH,
        RESOURCE_LIMIT_REACHED,
        PERSISTENCE_FAILURE,
        INTERNAL_ERROR
    }
}