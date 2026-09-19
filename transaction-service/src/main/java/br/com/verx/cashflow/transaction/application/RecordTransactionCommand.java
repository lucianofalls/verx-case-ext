package br.com.verx.cashflow.transaction.application;

import java.time.Instant;

public record RecordTransactionCommand(
        String merchantId,
        String type,
        String amount,
        String currency,
        String description,
        Instant occurredAt,
        String idempotencyKey
) {
}
