package br.com.verx.cashflow.transaction.adapters.in.web;

import java.time.Instant;

public record RecordTransactionRequest(
        String merchantId,
        String type,
        String amount,
        String currency,
        String description,
        Instant occurredAt
) {
}
