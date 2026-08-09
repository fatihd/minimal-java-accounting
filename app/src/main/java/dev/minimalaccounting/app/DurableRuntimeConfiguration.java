package dev.minimalaccounting.app;

import dev.minimalaccounting.adapters.messaging.artemis.ArtemisConfiguration;
import dev.minimalaccounting.adapters.persistence.postgresql.PostgresqlConfiguration;
import lombok.NonNull;

import java.util.Map;

record DurableRuntimeConfiguration(
        @NonNull PostgresqlConfiguration database,
        @NonNull ArtemisConfiguration broker,
        @NonNull String destinationPrefix) {
    private static final long RECEIVE_TIMEOUT_MILLIS = 50;

    DurableRuntimeConfiguration {
        if (destinationPrefix.isBlank()) {
            throw new IllegalArgumentException("destinationPrefix must not be blank");
        }
    }

    static DurableRuntimeConfiguration from(@NonNull Map<String, String> environment) {
        return new DurableRuntimeConfiguration(
                new PostgresqlConfiguration(
                        required(environment, "ACCOUNTING_DATABASE_URL"),
                        required(environment, "ACCOUNTING_DATABASE_USER"),
                        required(environment, "ACCOUNTING_DATABASE_PASSWORD")),
                new ArtemisConfiguration(
                        required(environment, "ACCOUNTING_BROKER_URL"),
                        required(environment, "ACCOUNTING_BROKER_USER"),
                        required(environment, "ACCOUNTING_BROKER_PASSWORD"),
                        RECEIVE_TIMEOUT_MILLIS),
                environment.getOrDefault("ACCOUNTING_DESTINATION_PREFIX", "accounting"));
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("missing environment variable " + name);
        }
        return value;
    }
}
