package br.com.verx.cashflow.transaction.adapters.in.web;

import java.util.List;

public record TransactionPageResponse(
        List<TransactionResponse> content,
        int page,
        int size,
        long totalElements
) {
}