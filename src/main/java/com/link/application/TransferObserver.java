package com.link.application;

import com.link.domain.TransferSession;

public interface TransferObserver {
    void onTransferUpdated(TransferSession snapshot);
}