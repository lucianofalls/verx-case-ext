package br.com.verx.cashflow.transaction.ports.out;

import br.com.verx.cashflow.transaction.domain.FinancialTransaction;
import br.com.verx.cashflow.transaction.domain.FinancialTransactionRecorded;

import java.util.List;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface TransactionPersistencePort {
    record TransactionPage(List<FinancialTransaction> content, int page, int size, long totalElements) {
    }

    Optional<FinancialTransaction> findById(UUID transactionId);

    Optional<FinancialTransaction> findByIdempotencyKey(String idempotencyKey);

    Optional<FinancialTransaction> findReversalByOriginalTransactionId(UUID originalTransactionId);

    TransactionPage findByMerchant(String merchantId, LocalDate from, LocalDate to, int page, int size);

    void saveTransactionAndOutbox(FinancialTransaction transaction, FinancialTransactionRecorded event);
}
