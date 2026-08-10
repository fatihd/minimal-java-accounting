package dev.minimalaccounting.infrastructure;

import org.junit.jupiter.api.Test;

import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JmxMetricsTest {
    @Test
    void registersAndUnregistersTheMetricsView() throws Exception {
        var server = ManagementFactory.getPlatformMBeanServer();
        var name = new ObjectName("dev.minimalaccounting:type=Metrics");
        var metrics = new MetricsRegistry(Clock.systemUTC());
        metrics.success("submit", 1, 1);

        try (var ignored = JmxMetrics.register(metrics)) {
            assertTrue(server.isRegistered(name));
            assertTrue(((String) server.getAttribute(name, "Snapshot"))
                    .contains("submit attempts=1"));
        }

        assertFalse(server.isRegistered(name));
    }
}
