package dev.minimalaccounting.model;

public class PostingRejectedException extends RuntimeException {
    public PostingRejectedException(String message) {
        super(message);
    }
}
