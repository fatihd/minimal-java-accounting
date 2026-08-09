package dev.minimalaccounting.shared;

public class PostingRejectedException extends RuntimeException {
    public PostingRejectedException(String message) {
        super(message);
    }
}
