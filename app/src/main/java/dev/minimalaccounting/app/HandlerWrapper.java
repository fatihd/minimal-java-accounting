package dev.minimalaccounting.app;

import dev.minimalaccounting.shared.ports.Handler;

interface HandlerWrapper {
    HandlerWrapper IDENTITY = new HandlerWrapper() {
        @Override
        public <T> Handler<T> apply(Handler<T> handler) {
            return handler;
        }
    };

    <T> Handler<T> apply(Handler<T> handler);

    default HandlerWrapper andThen(HandlerWrapper next) {
        HandlerWrapper current = this;
        return new HandlerWrapper() {
            @Override
            public <T> Handler<T> apply(Handler<T> handler) {
                return next.apply(current.apply(handler));
            }
        };
    }

    static HandlerWrapper identity() {
        return IDENTITY;
    }
}
