package dev.minimalaccounting.adapters.api.http;

import lombok.NonNull;

import java.net.InetSocketAddress;

public record HttpConfiguration(
        @NonNull InetSocketAddress address,
        int backlog,
        int maximumBodyBytes) {
    public HttpConfiguration {
        if (backlog < 0) {
            throw new IllegalArgumentException("backlog must not be negative");
        }
        if (maximumBodyBytes < 1) {
            throw new IllegalArgumentException("maximumBodyBytes must be positive");
        }
    }
}
