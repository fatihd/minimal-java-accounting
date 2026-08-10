package dev.minimalaccounting.adapters.messaging.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

final class JsonMessages {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private JsonMessages() {
    }

    static String write(Object message) {
        try {
            return MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Message cannot be serialized", failure);
        }
    }

    static <T> T read(String payload, Class<T> type) {
        try {
            return MAPPER.readValue(payload, type);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Message cannot be deserialized", failure);
        }
    }
}
