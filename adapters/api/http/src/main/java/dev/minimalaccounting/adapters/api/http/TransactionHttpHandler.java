package dev.minimalaccounting.adapters.api.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import dev.minimalaccounting.model.transaction.SubmitTransaction;
import dev.minimalaccounting.shared.ports.Handler;
import dev.minimalaccounting.model.AccountNumber;
import dev.minimalaccounting.model.DebitCredit;
import dev.minimalaccounting.model.Money;
import dev.minimalaccounting.model.PostingRejectedException;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;

@RequiredArgsConstructor
final class TransactionHttpHandler implements HttpHandler {
    private static final System.Logger LOGGER =
            System.getLogger(TransactionHttpHandler.class.getName());

    @NonNull
    private final Handler<SubmitTransaction> transactions;
    @NonNull
    private final ObjectMapper json;
    private final int maximumBodyBytes;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"/transactions".equals(exchange.getRequestURI().getPath())) {
                respond(exchange, 404, null);
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST");
                respond(exchange, 405, null);
                return;
            }
            if (!isJson(exchange.getRequestHeaders().getFirst("Content-Type"))) {
                respond(exchange, 415, "Content-Type must be application/json");
                return;
            }

            SubmitTransaction submitted;
            try {
                submitted = parse(readBody(exchange));
            } catch (BodyTooLargeException tooLarge) {
                respond(exchange, 413, tooLarge.getMessage());
                return;
            } catch (JsonProcessingException | InvalidRequestException invalid) {
                respond(exchange, 400, invalid.getMessage());
                return;
            } catch (RuntimeException invalid) {
                respond(exchange, 400, message(invalid));
                return;
            }

            try {
                transactions.handle(submitted);
                respond(exchange, 202, null);
            } catch (PostingRejectedException | IllegalArgumentException rejected) {
                respond(exchange, 422, message(rejected));
            } catch (RuntimeException failure) {
                LOGGER.log(System.Logger.Level.ERROR, "SubmitTransaction submission failed");
                respond(exchange, 500, "Internal server error");
            }
        } finally {
            exchange.close();
        }
    }

    private byte[] readBody(HttpExchange exchange) throws IOException {
        String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLength != null && Long.parseLong(contentLength) > maximumBodyBytes) {
            throw new BodyTooLargeException(maximumBodyBytes);
        }
        byte[] body = exchange.getRequestBody().readNBytes(maximumBodyBytes + 1);
        if (body.length > maximumBodyBytes) {
            throw new BodyTooLargeException(maximumBodyBytes);
        }
        return body;
    }

    private SubmitTransaction parse(byte[] body) throws JsonProcessingException {
        JsonNode root = json.readTree(new String(body, StandardCharsets.UTF_8));
        if (root == null || !root.isObject()) {
            throw invalid("request body must be a JSON object");
        }
        JsonNode linesNode = required(root, "lines");
        if (!linesNode.isArray()) {
            throw invalid("lines must be an array");
        }
        var lines = new ArrayList<SubmitTransaction.Line>();
        for (JsonNode line : linesNode) {
            if (!line.isObject()) {
                throw invalid("each line must be an object");
            }
            lines.add(new SubmitTransaction.Line(
                    new AccountNumber(text(line, "accountNumber")),
                    DebitCredit.valueOf(text(line, "side").toUpperCase(Locale.ROOT)),
                    money(required(line, "transactionAmount"), "transactionAmount"),
                    money(required(line, "ledgerAmount"), "ledgerAmount")));
        }
        return new SubmitTransaction(
                text(root, "requestId"),
                text(root, "documentType"),
                text(root, "documentNumber"),
                optionalText(root, "description"),
                Instant.parse(text(root, "occurredAt")),
                lines);
    }

    private static Money money(JsonNode node, String name) {
        if (!node.isObject()) {
            throw invalid(name + " must be an object");
        }
        JsonNode minor = required(node, "minor");
        if (!minor.isIntegralNumber() || !minor.canConvertToLong()) {
            throw invalid(name + ".minor must be a long integer");
        }
        return new Money(minor.longValue(), Currency.getInstance(text(node, "currency")));
    }

    private static String text(JsonNode parent, String name) {
        JsonNode node = required(parent, name);
        if (!node.isTextual() || node.textValue().isBlank()) {
            throw invalid(name + " must be a non-blank string");
        }
        return node.textValue();
    }

    private static String optionalText(JsonNode parent, String name) {
        JsonNode node = parent.get(name);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw invalid(name + " must be a string or null");
        }
        return node.textValue();
    }

    private static JsonNode required(JsonNode parent, String name) {
        JsonNode node = parent.get(name);
        if (node == null || node.isNull()) {
            throw invalid(name + " is required");
        }
        return node;
    }

    private static boolean isJson(String contentType) {
        return contentType != null
                && contentType.split(";", 2)[0].trim()
                .equalsIgnoreCase("application/json");
    }

    private void respond(HttpExchange exchange, int status, String message) throws IOException {
        if (message == null) {
            exchange.sendResponseHeaders(status, -1);
            return;
        }
        byte[] body = json.writeValueAsBytes(Map.of("message", message));
        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static InvalidRequestException invalid(String message) {
        return new InvalidRequestException(message);
    }

    private static String message(RuntimeException error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static final class InvalidRequestException extends RuntimeException {
        private InvalidRequestException(String message) {
            super(message);
        }
    }

    private static final class BodyTooLargeException extends RuntimeException {
        private BodyTooLargeException(int maximumBodyBytes) {
            super("request body exceeds " + maximumBodyBytes + " bytes");
        }
    }
}
