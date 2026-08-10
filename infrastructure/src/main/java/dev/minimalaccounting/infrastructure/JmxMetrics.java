package dev.minimalaccounting.infrastructure;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import javax.management.InstanceAlreadyExistsException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.StringJoiner;

@RequiredArgsConstructor
public final class JmxMetrics implements AutoCloseable {
    private static final String OBJECT_NAME = "dev.minimalaccounting:type=Metrics";

    @NonNull
    private final MBeanServer server;
    @NonNull
    private final ObjectName objectName;

    public static JmxMetrics register(@NonNull MetricsRegistry metrics) {
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
            ObjectName name = new ObjectName(OBJECT_NAME);
            server.registerMBean(new MetricsView(metrics), name);
            return new JmxMetrics(server, name);
        } catch (InstanceAlreadyExistsException duplicate) {
            throw new IllegalStateException("Accounting metrics are already registered", duplicate);
        } catch (JMException failure) {
            throw new IllegalStateException("Cannot register accounting metrics", failure);
        }
    }

    @Override
    public void close() {
        try {
            if (server.isRegistered(objectName)) {
                server.unregisterMBean(objectName);
            }
        } catch (JMException failure) {
            throw new IllegalStateException("Cannot unregister accounting metrics", failure);
        }
    }

    private record MetricsView(@NonNull MetricsRegistry metrics)
            implements MetricsViewMXBean {
        @Override
        public int getOperationCount() {
            return metrics.snapshot().operations().size();
        }

        @Override
        public String getSnapshot() {
            var snapshot = metrics.snapshot();
            StringJoiner lines = new StringJoiner(System.lineSeparator());
            snapshot.operations().forEach((name, value) -> lines.add(
                    name
                            + " attempts=" + value.attempts()
                            + " successes=" + value.successes()
                            + " failures=" + value.failures()
                            + " items=" + value.items()
                            + " totalNanoseconds=" + value.totalNanoseconds()));
            return lines.toString();
        }
    }
}
