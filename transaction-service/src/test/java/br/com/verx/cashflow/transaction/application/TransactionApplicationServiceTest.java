package br.com.verx.cashflow.transaction.application;

import br.com.verx.cashflow.transaction.ports.out.TransactionPersistencePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionApplicationServiceTest {
    @Mock
    private TransactionPersistencePort persistence;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Test
    void devePersistirLancamentoEOutboxNoMesmoCasoDeUso() {
        when(persistence.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        executeTransactionTemplate();

        TransactionResult result = service().record(new RecordTransactionCommand(
                "MERCHANT-001", "CREDIT", "10.0000", "BRL", null,
                java.time.Instant.parse("2026-09-18T13:00:00Z"), "key-1"));

        assertThat(result.replay()).isFalse();
        verify(persistence).saveTransactionAndOutbox(any(), any());
    }

    @Test
    void deveReutilizarLancamentoQuandoChaveForRepetida() {
        var existing = br.com.verx.cashflow.transaction.domain.FinancialTransaction.record(
                "MERCHANT-001", br.com.verx.cashflow.transaction.domain.TransactionType.CREDIT,
                br.com.verx.cashflow.transaction.domain.Money.parse("10.0000", "BRL"), null,
                java.time.Instant.parse("2026-09-18T13:00:00Z"), "key-1", java.time.Clock.systemUTC());
        when(persistence.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        TransactionResult result = service().record(new RecordTransactionCommand(
                "MERCHANT-001", "CREDIT", "10.0000", "BRL", null,
                existing.occurredAt(), "key-1"));

        assertThat(result.replay()).isTrue();
        assertThat(result.transaction()).isEqualTo(existing);
    }

    @Test
    void deveCriarReversaoIdempotenteDoLancamentoOriginal() {
        var original = br.com.verx.cashflow.transaction.domain.FinancialTransaction.record(
                "MERCHANT-001", br.com.verx.cashflow.transaction.domain.TransactionType.CREDIT,
                br.com.verx.cashflow.transaction.domain.Money.parse("10.0000", "BRL"), null,
                java.time.Instant.parse("2026-09-18T13:00:00Z"), "original-key",
                java.time.Clock.systemUTC());

        when(persistence.findByIdempotencyKey("reversal-key")).thenReturn(Optional.empty());
        when(persistence.findReversalByOriginalTransactionId(original.transactionId()))
                .thenReturn(Optional.empty());
        when(persistence.findById(original.transactionId())).thenReturn(Optional.of(original));
        executeTransactionTemplate();

        TransactionResult result = service().reverse(
                original.transactionId(),
                java.time.Instant.parse("2026-09-19T13:00:00Z"),
                "estorno",
                "reversal-key");

        assertThat(result.replay()).isFalse();
        assertThat(result.transaction().type())
                .isEqualTo(br.com.verx.cashflow.transaction.domain.TransactionType.DEBIT);
        assertThat(result.transaction().reversalOfTransactionId())
                .isEqualTo(original.transactionId());
        verify(persistence).saveTransactionAndOutbox(any(), any());
    }

    @Test
    void deveRecusarSegundaReversaoComOutraChave() {
        var original = br.com.verx.cashflow.transaction.domain.FinancialTransaction.record(
                "MERCHANT-001", br.com.verx.cashflow.transaction.domain.TransactionType.CREDIT,
                br.com.verx.cashflow.transaction.domain.Money.parse("10.0000", "BRL"), null,
                java.time.Instant.parse("2026-09-18T13:00:00Z"), "original-key",
                java.time.Clock.systemUTC());
        var existingReversal = br.com.verx.cashflow.transaction.domain.FinancialTransaction.reverse(
                original, "estorno", java.time.Instant.parse("2026-09-19T13:00:00Z"),
                "old-reversal-key", java.time.Clock.systemUTC());

        when(persistence.findByIdempotencyKey("new-reversal-key")).thenReturn(Optional.empty());
        when(persistence.findReversalByOriginalTransactionId(original.transactionId()))
                .thenReturn(Optional.of(existingReversal));
        executeTransactionTemplate();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service().reverse(
                original.transactionId(),
                java.time.Instant.parse("2026-09-19T13:00:00Z"),
                "estorno",
                "new-reversal-key"))
                .isInstanceOf(ReversalConflictException.class);
    }

    private TransactionApplicationService service() {
        return new TransactionApplicationService(persistence, transactionTemplate,
            new SimpleMeterRegistry());
    }

    private void executeTransactionTemplate() {
        doAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null))
                .when(transactionTemplate).execute(any());
    }
}
