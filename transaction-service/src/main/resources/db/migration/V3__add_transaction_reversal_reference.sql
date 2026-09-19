ALTER TABLE financial_transaction
    ADD COLUMN reversal_of_transaction_id UUID;

ALTER TABLE financial_transaction
    ADD CONSTRAINT financial_transaction_reversal_fk
    FOREIGN KEY (reversal_of_transaction_id)
    REFERENCES financial_transaction(transaction_id);

CREATE UNIQUE INDEX financial_transaction_one_reversal_per_original_uq
    ON financial_transaction (reversal_of_transaction_id)
    WHERE reversal_of_transaction_id IS NOT NULL;

ALTER TABLE financial_transaction
    ADD CONSTRAINT financial_transaction_not_self_reversal
    CHECK (reversal_of_transaction_id IS NULL OR reversal_of_transaction_id <> transaction_id);
