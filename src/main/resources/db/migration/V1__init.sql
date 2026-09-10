-- payments-api owns the `payments` database (las convenciones internas de API).
-- payments-worker writes into `settlements` but this repo owns the schema.

CREATE TABLE merchants (
    id            UUID PRIMARY KEY,
    name          TEXT NOT NULL,
    account_id    UUID NOT NULL,
    api_key_hash  TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE payments (
    id            UUID PRIMARY KEY,
    from_account  UUID NOT NULL,
    to_account    UUID NOT NULL,
    merchant_id   UUID NULL REFERENCES merchants(id),
    amount        NUMERIC(18,2) NOT NULL,
    currency      CHAR(3) NOT NULL,
    status        TEXT NOT NULL CHECK (status IN ('pending','approved','rejected','completed','failed')),
    fraud_score   NUMERIC(4,3),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_payments_from_account ON payments(from_account);
CREATE INDEX idx_payments_to_account ON payments(to_account);

-- IF NOT EXISTS: payments-worker también la crea si arranca primero (tiene su propia historia de Flyway)
CREATE TABLE IF NOT EXISTS settlements (
    id           UUID PRIMARY KEY,
    payment_id   UUID NOT NULL UNIQUE REFERENCES payments(id),
    settled_at   TIMESTAMPTZ NOT NULL,
    batch_ref    TEXT NOT NULL
);
