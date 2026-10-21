package com.link.domain;

public record Peer(
    PeerId id, 
    String displayName, 
    String candidateAddress, 
    boolean isTrusted
) {}