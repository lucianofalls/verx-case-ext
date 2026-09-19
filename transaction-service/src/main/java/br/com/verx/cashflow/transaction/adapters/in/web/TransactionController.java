package br.com.verx.cashflow.transaction.adapters.in.web;

import br.com.verx.cashflow.transaction.application.RecordTransactionCommand;
import br.com.verx.cashflow.transaction.application.TransactionApplicationService;
import br.com.verx.cashflow.transaction.application.TransactionResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/v1/transactions")
public class TransactionController {
    private final TransactionApplicationService service;

    public TransactionController(TransactionApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<TransactionResponse> record(
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestBody RecordTransactionRequest request) {
        TransactionResult result = service.record(new RecordTransactionCommand(
                request.merchantId(), request.type(), request.amount(), request.currency(),
                request.description(), request.occurredAt(), idempotencyKey));
        TransactionResponse response = TransactionResponse.from(result.transaction());
        if (result.replay()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.created(URI.create("/v1/transactions/" + response.transactionId()))
                .body(response);
    }

    @PostMapping("/{transactionId}/reversals")
    public ResponseEntity<TransactionResponse> reverse(
            @PathVariable(name = "transactionId") UUID transactionId,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestBody ReverseTransactionRequest request) {
        TransactionResult result = service.reverse(
                transactionId, request.occurredAt(), request.description(), idempotencyKey);
        TransactionResponse response = TransactionResponse.from(result.transaction());
        if (result.replay()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.created(URI.create("/v1/transactions/" + response.transactionId()))
                .body(response);
    }

    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionResponse> get(@PathVariable(name = "transactionId") UUID transactionId) {
        return service.findById(transactionId)
                .map(transaction -> ResponseEntity.ok(TransactionResponse.from(transaction)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public TransactionPageResponse list(@RequestParam(name = "merchantId") String merchantId,
                                          @RequestParam(name = "from", required = false) LocalDate from,
                                          @RequestParam(name = "to", required = false) LocalDate to,
                                          @RequestParam(name = "page", defaultValue = "0") int page,
                                          @RequestParam(name = "size", defaultValue = "50") int size) {
        if (page < 0 || size < 1 || size > 200 || (from != null && to != null && from.isAfter(to))) {
            throw new IllegalArgumentException("page/size inválidos");
        }
        var result = service.findByMerchant(merchantId, from, to, page, size);
        return new TransactionPageResponse(result.content().stream().map(TransactionResponse::from).toList(),
                result.page(), result.size(), result.totalElements());
    }
}
