ALTER TABLE outbox_event
    ADD COLUMN claim_token UUID,
    ADD COLUMN claimed_at TIMESTAMPTZ,
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE INDEX outbox_event_claim_idx
    ON outbox_event (next_attempt_at, created_at, id)
    WHERE published_at IS NULL;
