package dev.minimalaccounting.adapters.messaging.artemis;

import lombok.NonNull;
import org.apache.activemq.artemis.api.core.QueueConfiguration;
import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;

public final class ArtemisConnection implements AutoCloseable {
    private final ArtemisConfiguration configuration;
    private final ServerLocator locator;
    private final ClientSessionFactory sessions;

    public ArtemisConnection(@NonNull ArtemisConfiguration configuration) {
        this.configuration = configuration;
        try {
            locator = ActiveMQClient.createServerLocator(configuration.brokerUrl());
            sessions = locator.createSessionFactory();
        } catch (Exception failure) {
            throw new ArtemisFailure("connect to Artemis", failure);
        }
    }

    ClientSession session() {
        try {
            return sessions.createSession(
                    configuration.username(),
                    configuration.password(),
                    false,
                    true,
                    true,
                    false,
                    0);
        } catch (Exception failure) {
            throw new ArtemisFailure("create Artemis session", failure);
        }
    }

    long receiveTimeoutMillis() {
        return configuration.receiveTimeoutMillis();
    }

    void ensureQueue(String address, String queue, RoutingType routingType) {
        try (ClientSession session = session()) {
            SimpleString addressName = SimpleString.of(address);
            if (!session.addressQuery(addressName).isExists()) {
                session.createAddress(addressName, routingType, false);
            }
            SimpleString queueName = SimpleString.of(queue);
            if (!session.queueQuery(queueName).isExists()) {
                session.createQueue(QueueConfiguration.of(queueName)
                        .setAddress(addressName)
                        .setRoutingType(routingType)
                        .setDurable(true));
            }
        } catch (Exception failure) {
            throw new ArtemisFailure("declare Artemis destination", failure);
        }
    }

    void ensureAddress(String address, RoutingType routingType) {
        try (ClientSession session = session()) {
            SimpleString addressName = SimpleString.of(address);
            if (!session.addressQuery(addressName).isExists()) {
                session.createAddress(addressName, routingType, false);
            }
        } catch (Exception failure) {
            throw new ArtemisFailure("declare Artemis address", failure);
        }
    }

    @Override
    public void close() {
        sessions.close();
        locator.close();
    }
}
