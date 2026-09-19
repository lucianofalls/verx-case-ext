package br.com.verx.cashflow.consolidation.adapters.in.web;

import br.com.verx.cashflow.consolidation.application.model.DailyBalance;

import java.time.Instant;
import java.time.LocalDate;

public record DailyBalanceResponse(
        String merchantId,
        LocalDate businessDate,
        String currency,
        String totalCredits,
        String totalDebits,
        String balance,
        Instant updatedAt
) {
    public static DailyBalanceResponse from(DailyBalance balance) {
        return new DailyBalanceResponse(
                balance.merchantId(), balance.businessDate(), balance.currency(),
                balance.totalCredits().toPlainString(), balance.totalDebits().toPlainString(),
                balance.balance().toPlainString(), balance.updatedAt());
    }
}
