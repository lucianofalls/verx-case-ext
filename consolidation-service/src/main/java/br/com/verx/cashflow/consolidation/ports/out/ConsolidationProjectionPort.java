package br.com.verx.cashflow.consolidation.ports.out;

import br.com.verx.cashflow.consolidation.application.model.DailyBalance;
import br.com.verx.cashflow.consolidation.application.model.FinancialTransactionRecorded;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ConsolidationProjectionPort {

    boolean registerEvent(UUID eventId);

    void apply(FinancialTransactionRecorded event);

    Optional<DailyBalance> find(String merchantId, LocalDate businessDate, String currency);
}
