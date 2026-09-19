package br.com.verx.cashflow.consolidation.application.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record DailyBalance(
        String merchantId,
        LocalDate businessDate,
        String currency,
        BigDecimal totalCredits,
        BigDecimal totalDebits,
        BigDecimal balance,
        Instant updatedAt
) {
}
