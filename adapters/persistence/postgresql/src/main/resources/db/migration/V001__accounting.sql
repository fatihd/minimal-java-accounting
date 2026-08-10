CREATE TABLE account (
    revision BIGINT NOT NULL,
    account_number VARCHAR(100) PRIMARY KEY,
    name VARCHAR(300) NOT NULL,
    balance_minor BIGINT NOT NULL,
    currency CHAR(3) NOT NULL,
    minimum_balance_minor BIGINT,
    maximum_balance_minor BIGINT
);

CREATE TABLE account_hold (
    account_number VARCHAR(100) NOT NULL REFERENCES account(account_number) ON DELETE CASCADE,
    transaction_id BIGINT NOT NULL,
    effect_number INTEGER NOT NULL,
    side VARCHAR(10) NOT NULL,
    amount_minor BIGINT NOT NULL,
    state VARCHAR(20) NOT NULL,
    failure_reason TEXT,
    PRIMARY KEY (account_number, transaction_id, effect_number)
);

CREATE TABLE accounting_transaction (
    id BIGSERIAL PRIMARY KEY,
    revision BIGINT NOT NULL,
    state VARCHAR(30) NOT NULL,
    request_id VARCHAR(200) NOT NULL UNIQUE,
    document_type VARCHAR(100) NOT NULL,
    document_number VARCHAR(200) NOT NULL,
    description TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    held_at TIMESTAMPTZ,
    failure_reason TEXT
);

CREATE TABLE transaction_line (
    transaction_id BIGINT NOT NULL REFERENCES accounting_transaction(id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL,
    account_number VARCHAR(100) NOT NULL,
    side VARCHAR(10) NOT NULL,
    transaction_amount_minor BIGINT NOT NULL,
    transaction_currency CHAR(3) NOT NULL,
    ledger_amount_minor BIGINT NOT NULL,
    ledger_currency CHAR(3) NOT NULL,
    PRIMARY KEY (transaction_id, line_number)
);

CREATE TABLE account_effect (
    transaction_id BIGINT NOT NULL REFERENCES accounting_transaction(id) ON DELETE CASCADE,
    effect_number INTEGER NOT NULL,
    account_number VARCHAR(100) NOT NULL,
    side VARCHAR(10) NOT NULL,
    ledger_amount_minor BIGINT NOT NULL,
    ledger_currency CHAR(3) NOT NULL,
    state VARCHAR(20) NOT NULL,
    PRIMARY KEY (transaction_id, effect_number)
);

CREATE TABLE journal_entry (
    id BIGSERIAL PRIMARY KEY,
    revision BIGINT NOT NULL,
    request_id VARCHAR(200) NOT NULL UNIQUE,
    document_type VARCHAR(100) NOT NULL,
    document_number VARCHAR(200) NOT NULL,
    description TEXT,
    occurred_at TIMESTAMPTZ NOT NULL,
    journalized_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE journal_line (
    journal_entry_id BIGINT NOT NULL REFERENCES journal_entry(id) ON DELETE CASCADE,
    line_number INTEGER NOT NULL,
    account_number VARCHAR(100) NOT NULL,
    side VARCHAR(10) NOT NULL,
    transaction_amount_minor BIGINT NOT NULL,
    transaction_currency CHAR(3) NOT NULL,
    ledger_amount_minor BIGINT NOT NULL,
    ledger_currency CHAR(3) NOT NULL,
    balance_after_minor BIGINT NOT NULL,
    balance_currency CHAR(3) NOT NULL,
    PRIMARY KEY (journal_entry_id, line_number)
);
