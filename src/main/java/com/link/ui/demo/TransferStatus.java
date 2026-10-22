package com.link.ui.demo;

/**
 * Lifecycle state of a demo transfer. The prototype only mutates these values
 * in memory to drive the visual transfer cards; there is no real transfer
 * protocol behind them.
 */
public enum TransferStatus {
    PENDING,      // queued, waiting to start
    IN_PROGRESS,  // actively transferring (simulated progress)
    PAUSED,       // user-paused (demo only)
    COMPLETED,    // finished successfully
    FAILED,       // could not complete
    CANCELLED     // user-cancelled (demo only)
}
