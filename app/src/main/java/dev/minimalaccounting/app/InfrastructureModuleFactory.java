package dev.minimalaccounting.app;

import dev.minimalaccounting.app.adapters.ReceiverWrapper;
import dev.minimalaccounting.app.adapters.SenderWrapper;
import dev.minimalaccounting.infrastructure.BoundaryDecorators;
import dev.minimalaccounting.infrastructure.MetricsRegistry;
import dev.minimalaccounting.shared.ports.Receiver;
import dev.minimalaccounting.shared.ports.Sender;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Clock;

@RequiredArgsConstructor
final class InfrastructureModuleFactory {
    private final Clock clock;

    @Getter(lazy = true)
    private final MetricsRegistry metricsRegistry = new MetricsRegistry(clock);

    @Getter(lazy = true)
    private final BoundaryDecorators boundaryDecorators =
            new BoundaryDecorators(getMetricsRegistry());

    @Getter(lazy = true)
    private final SenderWrapper senderWrapper = new SenderWrapper() {
        @Override
        public <T> Sender<T> apply(String name, Sender<T> sender) {
            return getBoundaryDecorators().sender(name, sender);
        }
    };

    @Getter(lazy = true)
    private final ReceiverWrapper receiverWrapper = new ReceiverWrapper() {
        @Override
        public <T> Receiver<T> apply(String name, Receiver<T> receiver) {
            return getBoundaryDecorators().receiver(name, receiver);
        }
    };
}
