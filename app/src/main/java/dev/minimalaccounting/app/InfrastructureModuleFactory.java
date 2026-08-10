package dev.minimalaccounting.app;

import dev.minimalaccounting.infrastructure.MetricsRegistry;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Clock;

@RequiredArgsConstructor
final class InfrastructureModuleFactory {
    private final Clock clock;

    @Getter(lazy = true)
    private final MetricsRegistry metricsRegistry = new MetricsRegistry(clock);
}
