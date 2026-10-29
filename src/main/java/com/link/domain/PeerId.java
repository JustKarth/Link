package com.link.domain;

public record PeerId(String value) {
    @Override
    public String toString() {
        return value;
    }
}
