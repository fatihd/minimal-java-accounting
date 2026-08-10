package dev.minimalaccounting.infrastructure;

public interface MetricsViewMXBean {
    int getOperationCount();

    String getSnapshot();
}
