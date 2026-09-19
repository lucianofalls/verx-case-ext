package br.com.verx.cashflow.transaction.application;

public class ReversalConflictException extends RuntimeException {
    public ReversalConflictException(String message) {
        super(message);
    }
}
