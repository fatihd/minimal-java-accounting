package dev.minimalaccounting.infrastructure;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import java.util.function.ToLongFunction;

@RequiredArgsConstructor
final class OperationObserver {
    @NonNull
    private final MetricsRegistry metrics;
    @NonNull
    private final System.Logger logger;

    <T> Consumer<T> consumer(
            @NonNull String operation,
            @NonNull Consumer<? super T> delegate) {
        return argument -> observe(operation, () -> {
            delegate.accept(argument);
            return null;
        }, ignored -> 1);
    }

    <T> IntFunction<T> intFunction(
            @NonNull String operation,
            @NonNull IntFunction<? extends T> delegate,
            @NonNull ToLongFunction<? super T> itemCount) {
        return argument -> observe(
                operation,
                () -> delegate.apply(argument),
                itemCount);
    }

    private <T> T observe(
            String operation,
            Supplier<? extends T> invocation,
            ToLongFunction<? super T> itemCount) {
        long started = System.nanoTime();
        try {
            T result = invocation.get();
            metrics.success(
                    operation,
                    System.nanoTime() - started,
                    itemCount.applyAsLong(result));
            logger.log(System.Logger.Level.DEBUG, "{0} succeeded", operation);
            return result;
        } catch (RuntimeException failure) {
            metrics.failure(operation, System.nanoTime() - started);
            logger.log(System.Logger.Level.WARNING, "{0} failed", operation);
            throw failure;
        }
    }
}
