package br.com.verx.cashflow.consolidation.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FinancialTransactionRecorded(
        UUID eventId,
        UUID transactionId,
        String merchantId,
        LocalDate businessDate,
        TransactionType transactionType,
        BigDecimal amount,
        String currency,
        Instant eventAt
) {
    public enum TransactionType {
        CREDIT,
        DEBIT
    }
}
