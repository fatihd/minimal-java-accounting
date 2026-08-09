CREATE TABLE outbox_message (
    id BIGSERIAL PRIMARY KEY,
    destination VARCHAR(300) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ
);

CREATE INDEX outbox_message_unpublished
    ON outbox_message(id)
    WHERE published_at IS NULL;
