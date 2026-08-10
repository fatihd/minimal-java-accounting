package dev.minimalaccounting.adapters.messaging.outbox;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

final class ArtemisTestBroker {
    private static final String USERNAME = "accounting_test";
    private static final String PASSWORD = UUID.randomUUID().toString();
    private static final GenericContainer<?> BROKER = new GenericContainer<>(
            DockerImageName.parse("apache/artemis:2.55.0-alpine"))
            .withEnv("ARTEMIS_USER", USERNAME)
            .withEnv("ARTEMIS_PASSWORD", PASSWORD)
            .withEnv("ANONYMOUS_LOGIN", "false")
            .withExposedPorts(61616)
            .waitingFor(Wait.forListeningPort());

    static {
        BROKER.start();
    }

    private ArtemisTestBroker() {
    }

    static ArtemisConnection connect() {
        return new ArtemisConnection(new ArtemisConfiguration(
                "tcp://" + BROKER.getHost() + ':' + BROKER.getMappedPort(61616),
                USERNAME,
                PASSWORD,
                1_000));
    }

    static String uniqueName(String prefix) {
        return prefix + '.' + UUID.randomUUID();
    }
}
