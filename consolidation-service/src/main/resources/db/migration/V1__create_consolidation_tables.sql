CREATE TABLE daily_balance (
    merchant_id VARCHAR(64) NOT NULL CHECK (btrim(merchant_id) <> ''),
    business_date DATE NOT NULL,
    currency CHAR(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    total_credits NUMERIC(19,4) NOT NULL DEFAULT 0
        CHECK (total_credits >= 0 AND total_credits <> 'NaN'::numeric),
    total_debits NUMERIC(19,4) NOT NULL DEFAULT 0
        CHECK (total_debits >= 0 AND total_debits <> 'NaN'::numeric),
    balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    last_processed_event_id UUID,
    last_event_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (merchant_id, business_date, currency),
    CONSTRAINT daily_balance_totals_match CHECK (balance = total_credits - total_debits)
);

-- Insert eventId and update the balance in ONE transaction in the future consumer.
-- A duplicate eventId must not cause a second balance update (including A -> B -> A).
CREATE TABLE processed_event (
    event_id UUID PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
