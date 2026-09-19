package br.com.verx.cashflow.transaction.application;

import br.com.verx.cashflow.transaction.domain.FinancialTransaction;
import br.com.verx.cashflow.transaction.domain.Money;
import br.com.verx.cashflow.transaction.domain.TransactionType;
import br.com.verx.cashflow.transaction.ports.out.TransactionPersistencePort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Service
public class TransactionApplicationService {
    private static final Logger log = LoggerFactory.getLogger(TransactionApplicationService.class);
    private final TransactionPersistencePort persistence;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public TransactionApplicationService(TransactionPersistencePort persistence,
                                         TransactionTemplate transactionTemplate,
                                         MeterRegistry meterRegistry) {
        this.persistence = persistence;
        this.transactionTemplate = transactionTemplate;
        this.meterRegistry = meterRegistry;
        this.clock = Clock.systemUTC();
    }

    public TransactionResult record(RecordTransactionCommand command) {
        log.info("business_event=transaction_record_requested merchantId={} type={} amount={} currency={} occurredAt={}",
                command.merchantId(), command.type(), command.amount(), command.currency(), command.occurredAt());
        Optional<FinancialTransaction> existing = persistence.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return replayOrConflict(existing.get(), command);
        }

        try {
            return transactionTemplate.execute(status -> {
                Optional<FinancialTransaction> concurrent = persistence.findByIdempotencyKey(command.idempotencyKey());
                if (concurrent.isPresent()) {
                    return replayOrConflict(concurrent.get(), command);
                }
                FinancialTransaction transaction = FinancialTransaction.record(
                        command.merchantId(),
                        TransactionType.valueOf(command.type()),
                        Money.parse(command.amount(), command.currency()),
                        command.description(),
                        command.occurredAt(),
                        command.idempotencyKey(),
                        clock);
                persistence.saveTransactionAndOutbox(transaction, transaction.toRecordedEvent());
                meterRegistry.counter("cashflow.transactions.created").increment();
                log.info("business_event=transaction_recorded transactionId={} merchantId={} type={} amount={} currency={} businessDate={}",
                        transaction.transactionId(), transaction.merchantId(), transaction.type(),
                        transaction.amount().toContractString(), transaction.amount().currency(),
                        transaction.businessDate());
                return new TransactionResult(transaction, false);
            });
        } catch (DuplicateKeyException exception) {
            FinancialTransaction concurrent = persistence.findByIdempotencyKey(command.idempotencyKey())
                    .orElseThrow(() -> exception);
            return replayOrConflict(concurrent, command);
        }
    }

    public Optional<FinancialTransaction> findById(UUID id) {
        return persistence.findById(id);
    }

    public TransactionResult reverse(UUID originalTransactionId,
                                     Instant occurredAt,
                                     String description,
                                     String idempotencyKey) {
        FinancialTransaction existingByKey = persistence.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existingByKey != null) {
            return replayOrConflictReversal(existingByKey, originalTransactionId, occurredAt, description);
        }

        try {
            return transactionTemplate.execute(status -> {
                FinancialTransaction concurrentByKey = persistence.findByIdempotencyKey(idempotencyKey).orElse(null);
                if (concurrentByKey != null) {
                    return replayOrConflictReversal(concurrentByKey, originalTransactionId, occurredAt, description);
                }

                FinancialTransaction existingReversal = persistence
                        .findReversalByOriginalTransactionId(originalTransactionId)
                        .orElse(null);
                if (existingReversal != null) {
                    throw new ReversalConflictException("lançamento já possui reversão: "
                            + existingReversal.transactionId());
                }

                FinancialTransaction original = persistence.findById(originalTransactionId)
                        .orElseThrow(() -> new TransactionNotFoundException(
                                "lançamento original não encontrado: " + originalTransactionId));

                FinancialTransaction reversal = FinancialTransaction.reverse(
                        original, description, occurredAt, idempotencyKey, clock);

                persistence.saveTransactionAndOutbox(reversal, reversal.toRecordedEvent());
                meterRegistry.counter("cashflow.transactions.reversals.created").increment();
                log.info("business_event=transaction_reversal_recorded transactionId={} reversalOf={} merchantId={} type={} amount={} currency={} businessDate={}",
                        reversal.transactionId(), originalTransactionId, reversal.merchantId(), reversal.type(),
                        reversal.amount().toContractString(), reversal.amount().currency(), reversal.businessDate());

                return new TransactionResult(reversal, false);
            });
        } catch (DuplicateKeyException exception) {
            FinancialTransaction concurrentByKey = persistence.findByIdempotencyKey(idempotencyKey).orElse(null);
            if (concurrentByKey != null) {
                return replayOrConflictReversal(concurrentByKey, originalTransactionId, occurredAt, description);
            }
            FinancialTransaction existingReversal = persistence
                    .findReversalByOriginalTransactionId(originalTransactionId)
                    .orElse(null);
            if (existingReversal != null) {
                throw new ReversalConflictException("lançamento já possui reversão: "
                        + existingReversal.transactionId());
            }
            throw exception;
        }
    }

    public TransactionPersistencePort.TransactionPage findByMerchant(
            String merchantId, LocalDate from, LocalDate to, int page, int size) {
        return persistence.findByMerchant(merchantId, from, to, page, size);
    }

    private TransactionResult replayOrConflictReversal(FinancialTransaction existing,
                                                       UUID originalTransactionId,
                                                       Instant occurredAt,
                                                       String description) {
        boolean same = originalTransactionId.equals(existing.reversalOfTransactionId())
                && existing.occurredAt().equals(occurredAt)
                && java.util.Objects.equals(existing.description(), description);
        if (!same) {
            throw new IdempotencyConflictException(
                    "Idempotency-Key já utilizada com outra operação/corpo");
        }
        log.info("business_event=transaction_reversal_replayed transactionId={} reversalOf={}",
                existing.transactionId(), originalTransactionId);
        return new TransactionResult(existing, true);
    }

    private TransactionResult replayOrConflict(FinancialTransaction existing, RecordTransactionCommand command) {
        boolean same = existing.merchantId().equals(command.merchantId())
                && existing.type().name().equals(command.type())
                && existing.amount().equals(Money.parse(command.amount(), command.currency()))
                && java.util.Objects.equals(existing.description(), command.description())
                && existing.occurredAt().equals(command.occurredAt());
        if (!same) {
            log.warn("business_event=idempotency_conflict transactionId={} merchantId={}",
                    existing.transactionId(), existing.merchantId());
            throw new IdempotencyConflictException("Idempotency-Key já utilizada com corpo diferente");
        }
        log.info("business_event=transaction_replayed transactionId={} merchantId={}",
                existing.transactionId(), existing.merchantId());
        return new TransactionResult(existing, true);
    }

}
