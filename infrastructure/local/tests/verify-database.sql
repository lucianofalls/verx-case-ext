\set ON_ERROR_STOP on
BEGIN;
SET LOCAL ROLE transaction_app;
SET LOCAL search_path = transactions;
INSERT INTO financial_transaction
(transaction_id, merchant_id, transaction_type, amount, currency, occurred_at, business_date, idempotency_key)
VALUES ('00000000-0000-0000-0000-000000000001', 'SCHEMA-TEST', 'CREDIT', 10.1234, 'BRL', '2026-09-18T02:30:00Z', '2026-09-17', 'schema-test');
INSERT INTO outbox_event (id, aggregate_id, event_type, event_version, payload)
VALUES ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'FinancialTransactionRecorded', 1, '{}');
DO $$
BEGIN
    IF (SELECT amount FROM financial_transaction WHERE idempotency_key = 'schema-test') <> 10.1234 THEN
        RAISE EXCEPTION 'Decimal precision lost';
    END IF;
    BEGIN
        INSERT INTO financial_transaction SELECT '00000000-0000-0000-0000-000000000003'::uuid,
        merchant_id, transaction_type, amount, currency, description, occurred_at, business_date, idempotency_key, created_at
        FROM financial_transaction WHERE idempotency_key = 'schema-test';
        RAISE EXCEPTION 'Duplicate idempotency key accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        UPDATE financial_transaction SET description = 'changed' WHERE idempotency_key = 'schema-test';
        RAISE EXCEPTION 'Mutable transaction accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO financial_transaction VALUES ('00000000-0000-0000-0000-000000000003', 'TEST', 'DEBIT', -1, 'BRL', NULL, now(), current_date, 'negative-test', now());
        RAISE EXCEPTION 'Negative amount accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO financial_transaction VALUES ('00000000-0000-0000-0000-000000000003', 'TEST', 'DEBIT', 1, 'BRL', NULL, '2026-09-18T02:30:00Z', '2026-09-18', 'date-test', now());
        RAISE EXCEPTION 'Wrong business date accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO financial_transaction VALUES ('00000000-0000-0000-0000-000000000003', 'TEST', 'DEBIT', 1000000000000000, 'BRL', NULL, now(), current_date, 'overflow-test', now());
        RAISE EXCEPTION 'Numeric overflow accepted';
    EXCEPTION WHEN numeric_value_out_of_range THEN NULL;
    END;
    BEGIN
        INSERT INTO financial_transaction VALUES ('00000000-0000-0000-0000-000000000003', 'TEST', 'OTHER', 1, 'BRL', NULL, '2026-09-17T13:00:00Z', '2026-09-17', 'type-test', now());
        RAISE EXCEPTION 'Invalid transaction type accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO financial_transaction VALUES ('00000000-0000-0000-0000-000000000003', 'TEST', 'DEBIT', 1, 'brl', NULL, '2026-09-17T13:00:00Z', '2026-09-17', 'currency-test', now());
        RAISE EXCEPTION 'Invalid currency format accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO financial_transaction VALUES ('00000000-0000-0000-0000-000000000003', 'TEST', 'DEBIT', 'NaN', 'BRL', NULL, '2026-09-17T13:00:00Z', '2026-09-17', 'nan-test', now());
        RAISE EXCEPTION 'NaN accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        PERFORM * FROM consolidation.daily_balance;
        RAISE EXCEPTION 'Cross-schema read permitted';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
END $$;
RESET ROLE;
SET LOCAL ROLE consolidation_app;
SET LOCAL search_path = consolidation;
INSERT INTO daily_balance (merchant_id, business_date, currency, total_credits, total_debits, balance)
VALUES ('SCHEMA-TEST', '2026-09-17', 'BRL', 10.1234, 20, -9.8766);
INSERT INTO processed_event (event_id) VALUES ('00000000-0000-0000-0000-000000000001');
INSERT INTO processed_event (event_id) VALUES ('00000000-0000-0000-0000-000000000002');
DO $$
BEGIN
    BEGIN
        INSERT INTO processed_event (event_id) VALUES ('00000000-0000-0000-0000-000000000001');
        RAISE EXCEPTION 'Repeated old event accepted after newer event';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        UPDATE daily_balance SET balance = 999 WHERE merchant_id = 'SCHEMA-TEST';
        RAISE EXCEPTION 'Inconsistent balance accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        PERFORM * FROM transactions.financial_transaction;
        RAISE EXCEPTION 'Cross-schema read permitted';
    EXCEPTION WHEN insufficient_privilege THEN NULL;
    END;
END $$;
ROLLBACK;
SELECT 'Database constraints and schema isolation verified; all test data rolled back.' AS result;
