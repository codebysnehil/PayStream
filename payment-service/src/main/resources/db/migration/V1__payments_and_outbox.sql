CREATE TABLE payments (
    id                  UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    idempotency_key     NVARCHAR(100)    NOT NULL,
    request_hash        CHAR(64)         NOT NULL,
    debtor_account      NVARCHAR(34)     NOT NULL,
    creditor_account    NVARCHAR(34)     NOT NULL,
    amount              DECIMAL(19, 4)   NOT NULL,
    currency            CHAR(3)          NOT NULL,
    beneficiary_country CHAR(2)          NOT NULL,
    status              NVARCHAR(32)     NOT NULL,
    risk_score          INT              NULL,
    status_reason       NVARCHAR(500)    NULL,
    created_at          DATETIME2        NOT NULL,
    updated_at          DATETIME2        NOT NULL,
    version             BIGINT           NOT NULL,
    CONSTRAINT uq_payments_idempotency_key UNIQUE (idempotency_key)
);

CREATE INDEX ix_payments_debtor_created ON payments (debtor_account, created_at);

-- Transactional outbox: written in the same DB transaction as the payment,
-- relayed to Kafka asynchronously so we never lose or invent an event.
CREATE TABLE outbox_events (
    id           UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    aggregate_id UNIQUEIDENTIFIER NOT NULL,
    topic        NVARCHAR(200)    NOT NULL,
    message_key  NVARCHAR(100)    NOT NULL,
    event_type   NVARCHAR(200)    NOT NULL,
    payload      NVARCHAR(MAX)    NOT NULL,
    created_at   DATETIME2        NOT NULL,
    published_at DATETIME2        NULL,
    attempts     INT              NOT NULL
);

CREATE INDEX ix_outbox_pending ON outbox_events (published_at, created_at);
