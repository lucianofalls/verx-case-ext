package br.com.verx.cashflow.transaction.domain;

/**
 * Lançada quando uma regra/invariante do domínio de lançamentos é violada.
 *
 * <p>Não depende de nenhum framework — o adapter de entrada (Controller) é
 * quem decide como traduzir isso para HTTP (422, conforme
 * {@code docs/contracts/rest/transaction-service.openapi.yaml}).
 */
public class InvalidTransactionException extends RuntimeException {

    public InvalidTransactionException(String message) {
        super(message);
    }
}
