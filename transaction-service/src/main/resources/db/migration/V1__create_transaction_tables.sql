CREATE TABLE financial_transaction (
    transaction_id UUID PRIMARY KEY,
    merchant_id VARCHAR(64) NOT NULL CHECK (btrim(merchant_id) <> ''),
    transaction_type VARCHAR(6) NOT NULL CHECK (transaction_type IN ('CREDIT', 'DEBIT')),
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0 AND amount <> 'NaN'::numeric),
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    description VARCHAR(500),
    occurred_at TIMESTAMPTZ NOT NULL,
    business_date DATE NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE CHECK (btrim(idempotency_key) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT business_date_matches_timezone CHECK (
        business_date = (occurred_at AT TIME ZONE 'America/Sao_Paulo')::date
    )
);
CREATE INDEX financial_transaction_merchant_date_idx
    ON financial_transaction (merchant_id, business_date, currency);

CREATE FUNCTION reject_financial_transaction_mutation() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Financial transactions are immutable' USING ERRCODE = '23514';
END;
$$;
CREATE TRIGGER financial_transaction_immutable
BEFORE UPDATE OR DELETE ON financial_transaction
FOR EACH ROW EXECUTE FUNCTION reject_financial_transaction_mutation();

CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL REFERENCES financial_transaction(transaction_id),
    event_type VARCHAR(255) NOT NULL,
    event_version INTEGER NOT NULL CHECK (event_version > 0),
    payload JSONB NOT NULL CHECK (jsonb_typeof(payload) = 'object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0)
);
CREATE INDEX outbox_event_pending_idx ON outbox_event (created_at, id)
    WHERE published_at IS NULL;
