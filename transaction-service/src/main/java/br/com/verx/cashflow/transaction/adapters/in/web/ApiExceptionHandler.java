package br.com.verx.cashflow.transaction.adapters.in.web;

import br.com.verx.cashflow.transaction.application.IdempotencyConflictException;
import br.com.verx.cashflow.transaction.application.ReversalConflictException;
import br.com.verx.cashflow.transaction.application.TransactionNotFoundException;
import br.com.verx.cashflow.transaction.domain.InvalidTransactionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({IdempotencyConflictException.class, ReversalConflictException.class})
    ProblemDetail conflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    ProblemDetail notFound(TransactionNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({InvalidTransactionException.class, IllegalArgumentException.class})
    ProblemDetail invalid(RuntimeException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno ao processar a requisição");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}
