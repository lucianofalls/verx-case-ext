package br.com.verx.cashflow.transaction.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Domain Event emitido quando um {@link FinancialTransaction} é confirmado.
 *
 * <p>O envelope CloudEvents pertence ao adapter de saída; o domínio carrega
 * somente dados de negócio.
 */
public record FinancialTransactionRecorded(
        UUID transactionId,
        String merchantId,
        TransactionType transactionType,
        Money amount,
        LocalDate businessDate,
        Instant occurredAt,
        UUID reversalOfTransactionId
) {
    public FinancialTransactionRecorded {
        if (transactionId == null) throw new InvalidTransactionException("transactionId não pode ser nulo");
        if (merchantId == null || merchantId.isBlank()) throw new InvalidTransactionException("merchantId não pode ser vazio");
        if (transactionType == null) throw new InvalidTransactionException("transactionType não pode ser nulo");
        if (amount == null) throw new InvalidTransactionException("amount não pode ser nulo");
        if (businessDate == null) throw new InvalidTransactionException("businessDate não pode ser nulo");
        if (occurredAt == null) throw new InvalidTransactionException("occurredAt não pode ser nulo");
    }

    public static FinancialTransactionRecorded from(FinancialTransaction transaction) {
        return new FinancialTransactionRecorded(
                transaction.transactionId(),
                transaction.merchantId(),
                transaction.type(),
                transaction.amount(),
                transaction.businessDate(),
                transaction.occurredAt(),
                transaction.reversalOfTransactionId()
        );
    }
}
