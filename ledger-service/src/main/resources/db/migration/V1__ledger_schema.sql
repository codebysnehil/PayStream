CREATE TABLE accounts (
    account_number NVARCHAR(34)   NOT NULL PRIMARY KEY,
    holder_name    NVARCHAR(200)  NOT NULL,
    currency       CHAR(3)        NOT NULL,
    balance        DECIMAL(19, 4) NOT NULL,
    updated_at     DATETIME2      NOT NULL,
    version        BIGINT         NOT NULL,
    -- Last line of defence: the database itself refuses an overdrawn account.
    CONSTRAINT ck_accounts_balance_non_negative CHECK (balance >= 0)
);

-- Append-only double-entry journal: every settled payment writes exactly one DEBIT and one CREDIT.
CREATE TABLE ledger_entries (
    id             BIGINT IDENTITY(1, 1) NOT NULL PRIMARY KEY,
    payment_id     UNIQUEIDENTIFIER      NOT NULL,
    account_number NVARCHAR(34)          NOT NULL,
    direction      NVARCHAR(6)           NOT NULL,
    amount         DECIMAL(19, 4)        NOT NULL,
    currency       CHAR(3)               NOT NULL,
    created_at     DATETIME2             NOT NULL,
    CONSTRAINT fk_ledger_entries_account FOREIGN KEY (account_number) REFERENCES accounts (account_number),
    CONSTRAINT uq_ledger_entries_payment_direction UNIQUE (payment_id, direction)
);

CREATE INDEX ix_ledger_entries_account ON ledger_entries (account_number, id);

-- One row per payment: the idempotency record that makes settlement exactly-once in effect.
CREATE TABLE settlements (
    payment_id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    outcome    NVARCHAR(16)     NOT NULL,
    reason     NVARCHAR(100)    NULL,
    created_at DATETIME2        NOT NULL
);
