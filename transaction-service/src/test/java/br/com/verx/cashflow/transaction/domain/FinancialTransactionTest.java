package br.com.verx.cashflow.transaction.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinancialTransactionTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-17T13:00:00Z"), ZoneOffset.UTC);

    @Test
    void deveRegistrarLancamentoValido() {
        Money amount = Money.parse("1500.00", "BRL");
        Instant occurredAt = Instant.parse("2026-09-17T13:00:00Z");

        FinancialTransaction tx = FinancialTransaction.record(
                "MERCHANT-001", TransactionType.CREDIT, amount, "Venda no cartão",
                occurredAt, "idem-key-1", FIXED_CLOCK);

        assertThat(tx.transactionId()).isNotNull();
        assertThat(tx.merchantId()).isEqualTo("MERCHANT-001");
        assertThat(tx.type()).isEqualTo(TransactionType.CREDIT);
        assertThat(tx.amount()).isEqualTo(amount);
        assertThat(tx.createdAt()).isEqualTo(Instant.parse("2026-09-17T13:00:00Z"));
    }

    @Test
    void doisLancamentosDevemTerTransactionIdsDiferentes() {
        Money amount = Money.parse("10.00", "BRL");
        Instant occurredAt = Instant.parse("2026-09-17T13:00:00Z");

        FinancialTransaction tx1 = FinancialTransaction.record(
                "MERCHANT-001", TransactionType.CREDIT, amount, null, occurredAt, "idem-1", FIXED_CLOCK);
        FinancialTransaction tx2 = FinancialTransaction.record(
                "MERCHANT-001", TransactionType.CREDIT, amount, null, occurredAt, "idem-2", FIXED_CLOCK);

        assertThat(tx1.transactionId()).isNotEqualTo(tx2.transactionId());
    }

    @Test
    void deveDerivarBusinessDateNoTimezoneDeSaoPaulo() {
        // 02:30 UTC = 23:30 do dia anterior em America/Sao_Paulo (UTC-3, sem DST desde 2019).
        // Este é exatamente o caso que a decisão de timezone (seção 6.0 do case) existe para cobrir:
        // sem fixar o timezone, businessDate seria ambíguo perto da virada do dia.
        Instant occurredAt = Instant.parse("2026-09-18T02:30:00Z");
        Money amount = Money.parse("10.00", "BRL");

        FinancialTransaction tx = FinancialTransaction.record(
                "MERCHANT-001", TransactionType.DEBIT, amount, null, occurredAt, "idem-1", FIXED_CLOCK);

        assertThat(tx.businessDate()).isEqualTo(LocalDate.of(2026, 9, 17));
    }

    @Test
    void deveRejeitarMerchantIdVazio() {
        assertThatThrownBy(() -> FinancialTransaction.record(
                " ", TransactionType.CREDIT, Money.parse("10.00", "BRL"), null,
                Instant.now(), "idem-1", FIXED_CLOCK))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("merchantId");
    }

    @Test
    void deveRejeitarIdempotencyKeyVazia() {
        assertThatThrownBy(() -> FinancialTransaction.record(
                "MERCHANT-001", TransactionType.CREDIT, Money.parse("10.00", "BRL"), null,
                Instant.now(), "", FIXED_CLOCK))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("idempotencyKey");
    }

    @Test
    void deveRejeitarDescricaoMuitoLonga() {
        String descricaoGigante = "a".repeat(501);

        assertThatThrownBy(() -> FinancialTransaction.record(
                "MERCHANT-001", TransactionType.CREDIT, Money.parse("10.00", "BRL"), descricaoGigante,
                Instant.now(), "idem-1", FIXED_CLOCK))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("description");
    }

    @Test
    void deveRejeitarTypeNulo() {
        assertThatThrownBy(() -> FinancialTransaction.record(
                "MERCHANT-001", null, Money.parse("10.00", "BRL"), null,
                Instant.now(), "idem-1", FIXED_CLOCK))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("type");
    }

    @Test
    void igualdadeDeveSerPorTransactionIdNaoPorConteudo() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-17T13:00:00Z");
        Money amount = Money.parse("10.00", "BRL");

        FinancialTransaction tx1 = FinancialTransaction.reconstitute(
                id, "MERCHANT-001", TransactionType.CREDIT, amount, null,
                now, LocalDate.of(2026, 9, 17), "idem-1", now, null);
        // mesmo id, conteúdo diferente (hipotético — não deveria acontecer na prática,
        // já que o objeto é imutável, mas prova que a igualdade é por identidade)
        FinancialTransaction tx2 = FinancialTransaction.reconstitute(
                id, "MERCHANT-002", TransactionType.DEBIT, Money.parse("99.00", "BRL"), "outra",
                now, LocalDate.of(2026, 9, 17), "idem-2", now, null);

        assertThat(tx1).isEqualTo(tx2);
        assertThat(tx1.hashCode()).isEqualTo(tx2.hashCode());
    }

    @Test
    void deveCriarReversaoComoLancamentoCompensatorio() {
        FinancialTransaction original = FinancialTransaction.record(
                "MERCHANT-001", TransactionType.CREDIT, Money.parse("100.00", "BRL"), "venda",
                Instant.parse("2026-09-17T13:00:00Z"), "idem-original", FIXED_CLOCK);

        FinancialTransaction reversal = FinancialTransaction.reverse(
                original, "estorno", Instant.parse("2026-09-18T13:00:00Z"),
                "idem-reversal", FIXED_CLOCK);

        assertThat(reversal.transactionId()).isNotEqualTo(original.transactionId());
        assertThat(reversal.type()).isEqualTo(TransactionType.DEBIT);
        assertThat(reversal.amount()).isEqualTo(original.amount());
        assertThat(reversal.reversalOfTransactionId()).isEqualTo(original.transactionId());
        assertThat(original.reversalOfTransactionId()).isNull();
    }

    @Test
    void naoDevePermitirReverterUmaReversao() {
        FinancialTransaction original = FinancialTransaction.record(
                "MERCHANT-001", TransactionType.CREDIT, Money.parse("100.00", "BRL"), null,
                Instant.parse("2026-09-17T13:00:00Z"), "idem-original", FIXED_CLOCK);
        FinancialTransaction reversal = FinancialTransaction.reverse(
                original, null, Instant.parse("2026-09-18T13:00:00Z"),
                "idem-reversal", FIXED_CLOCK);

        assertThatThrownBy(() -> FinancialTransaction.reverse(
                reversal, null, Instant.parse("2026-09-19T13:00:00Z"),
                "idem-second", FIXED_CLOCK))
                .isInstanceOf(InvalidTransactionException.class)
                .hasMessageContaining("já é reversão");
    }

    @Test
    void toRecordedEventDeveCarregarOsDadosDoLancamento() {
        Money amount = Money.parse("1500.00", "BRL");
        Instant occurredAt = Instant.parse("2026-09-17T13:00:00Z");

        FinancialTransaction tx = FinancialTransaction.record(
                "MERCHANT-001", TransactionType.CREDIT, amount, null,
                occurredAt, "idem-1", FIXED_CLOCK);

        FinancialTransactionRecorded event = tx.toRecordedEvent();

        assertThat(event.transactionId()).isEqualTo(tx.transactionId());
        assertThat(event.merchantId()).isEqualTo("MERCHANT-001");
        assertThat(event.transactionType()).isEqualTo(TransactionType.CREDIT);
        assertThat(event.amount()).isEqualTo(amount);
        assertThat(event.businessDate()).isEqualTo(tx.businessDate());
    }
}
