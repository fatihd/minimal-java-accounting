package dev.minimalaccounting.observability;

public interface MetricsViewMXBean {
    int getOperationCount();

    String getSnapshot();
}
