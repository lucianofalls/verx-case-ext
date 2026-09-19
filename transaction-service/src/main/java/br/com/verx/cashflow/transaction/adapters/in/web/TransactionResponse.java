package br.com.verx.cashflow.transaction.adapters.in.web;

import br.com.verx.cashflow.transaction.domain.FinancialTransaction;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionResponse(
        UUID transactionId,
        String merchantId,
        String type,
        String amount,
        String currency,
        String description,
        Instant occurredAt,
        LocalDate businessDate,
        Instant createdAt,
        UUID reversalOfTransactionId
) {
    public static TransactionResponse from(FinancialTransaction transaction) {
        return new TransactionResponse(
                transaction.transactionId(), transaction.merchantId(), transaction.type().name(),
                transaction.amount().toContractString(), transaction.amount().currency(),
                transaction.description(), transaction.occurredAt(), transaction.businessDate(),
                transaction.createdAt(), transaction.reversalOfTransactionId());
    }
}
