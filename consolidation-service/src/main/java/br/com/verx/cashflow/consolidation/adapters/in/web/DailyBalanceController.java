package br.com.verx.cashflow.consolidation.adapters.in.web;

import br.com.verx.cashflow.consolidation.application.ConsolidationApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/v1/merchants")
public class DailyBalanceController {
    private final ConsolidationApplicationService service;

    public DailyBalanceController(ConsolidationApplicationService service) {
        this.service = service;
    }

    @GetMapping("/{merchantId}/daily-balances/{date}")
    public ResponseEntity<DailyBalanceResponse> get(
            @PathVariable(name = "merchantId") String merchantId,
            @PathVariable(name = "date") LocalDate date,
            @RequestParam(name = "currency", defaultValue = "BRL") String currency) {
        return service.find(merchantId, date, currency)
                .map(balance -> ResponseEntity.ok(DailyBalanceResponse.from(balance)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
