package br.com.verx.cashflow.transaction.domain;

/**
 * Tipo de um lançamento financeiro.
 *
 * <p>Referência de domínio: BIAN 14.0 — Position Keeping (débito/crédito
 * contra uma posição financeira).
 */
public enum TransactionType {
    CREDIT,
    DEBIT;

    public TransactionType opposite() {
        return this == CREDIT ? DEBIT : CREDIT;
    }
}
