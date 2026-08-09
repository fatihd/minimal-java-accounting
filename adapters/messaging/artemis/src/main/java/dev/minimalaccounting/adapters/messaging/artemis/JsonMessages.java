package dev.minimalaccounting.adapters.messaging.artemis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

final class JsonMessages<T> {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private final Class<T> type;

    JsonMessages(Class<T> type) {
        this.type = type;
    }

    String write(T message) {
        try {
            return MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Message cannot be serialized", failure);
        }
    }

    T read(String payload) {
        try {
            return MAPPER.readValue(payload, type);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Message cannot be deserialized", failure);
        }
    }
}
