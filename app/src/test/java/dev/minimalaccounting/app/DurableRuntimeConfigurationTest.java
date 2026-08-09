package dev.minimalaccounting.app;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DurableRuntimeConfigurationTest {
    @Test
    void createsAdapterConfigurationFromEnvironment() {
        var configuration = DurableRuntimeConfiguration.from(Map.of(
                "ACCOUNTING_DATABASE_URL", "jdbc:postgresql://database/accounting",
                "ACCOUNTING_DATABASE_USER", "database-user",
                "ACCOUNTING_DATABASE_PASSWORD", "database-password",
                "ACCOUNTING_BROKER_URL", "tcp://broker:61616",
                "ACCOUNTING_BROKER_USER", "broker-user",
                "ACCOUNTING_BROKER_PASSWORD", "broker-password",
                "ACCOUNTING_DESTINATION_PREFIX", "instance-one"));

        assertEquals("jdbc:postgresql://database/accounting",
                configuration.database().jdbcUrl());
        assertEquals("tcp://broker:61616", configuration.broker().brokerUrl());
        assertEquals("instance-one", configuration.destinationPrefix());
    }

    @Test
    void reportsTheFirstMissingRequiredSetting() {
        var failure = assertThrows(
                IllegalArgumentException.class,
                () -> DurableRuntimeConfiguration.from(Map.of()));

        assertEquals(
                "missing environment variable ACCOUNTING_DATABASE_URL",
                failure.getMessage());
    }
}
