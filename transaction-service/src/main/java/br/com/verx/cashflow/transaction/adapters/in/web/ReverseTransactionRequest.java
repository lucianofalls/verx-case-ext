package br.com.verx.cashflow.transaction.adapters.in.web;

import java.time.Instant;

public record ReverseTransactionRequest(
        Instant occurredAt,
        String description
) {
}
