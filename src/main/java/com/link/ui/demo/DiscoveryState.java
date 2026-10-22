package com.link.ui.demo;

/**
 * High-level state of the mock device-discovery engine. Drives the status
 * indicator on the Nearby screen and the overlay shown inside the network
 * visualization.
 */
public enum DiscoveryState {
    LOADING,      // searching for nearby devices
    FOUND,        // one or more devices discovered
    NONE_FOUND,   // search finished, nothing nearby
    PAUSED,       // discovery disabled by the user (or via settings)
    UNAVAILABLE   // discovery cannot run in this prototype
}
