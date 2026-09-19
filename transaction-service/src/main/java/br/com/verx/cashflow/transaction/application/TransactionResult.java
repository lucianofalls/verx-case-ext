package br.com.verx.cashflow.transaction.application;

import br.com.verx.cashflow.transaction.domain.FinancialTransaction;

public record TransactionResult(FinancialTransaction transaction, boolean replay) {
}
