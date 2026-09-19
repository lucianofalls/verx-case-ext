package br.com.verx.cashflow.consolidation.application;

import br.com.verx.cashflow.consolidation.application.model.DailyBalance;
import br.com.verx.cashflow.consolidation.application.model.FinancialTransactionRecorded;
import br.com.verx.cashflow.consolidation.adapters.out.observability.ConsolidationMetrics;
import br.com.verx.cashflow.consolidation.ports.out.ConsolidationProjectionPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class ConsolidationApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ConsolidationApplicationService.class);

    private final ConsolidationProjectionPort projectionPort;
    private final MeterRegistry meterRegistry;
    private final ConsolidationMetrics consolidationMetrics;

    public ConsolidationApplicationService(ConsolidationProjectionPort projectionPort,
                                           MeterRegistry meterRegistry,
                                           ConsolidationMetrics consolidationMetrics) {
        this.projectionPort = projectionPort;
        this.meterRegistry = meterRegistry;
        this.consolidationMetrics = consolidationMetrics;
    }

    @Transactional
    public void process(FinancialTransactionRecorded event) {
        log.info("business_event=consolidation_event_received eventId={} merchantId={} type={} amount={} currency={} businessDate={}",
                event.eventId(), event.merchantId(), event.transactionType(),
                event.amount().toPlainString(), event.currency(), event.businessDate());

        if (!projectionPort.registerEvent(event.eventId())) {
            meterRegistry.counter("cashflow.consolidation.events.duplicates").increment();
            log.info("business_event=consolidation_event_duplicate eventId={} merchantId={}",
                    event.eventId(), event.merchantId());
            return;
        }

        projectionPort.apply(event);
        consolidationMetrics.recordProcessedEvent(event.eventAt());

        meterRegistry.counter("cashflow.consolidation.events.processed").increment();
        log.info("business_event=daily_balance_projected eventId={} merchantId={} businessDate={} type={} amount={} currency={}",
                event.eventId(), event.merchantId(), event.businessDate(),
                event.transactionType(), event.amount().toPlainString(), event.currency());
    }

    public Optional<DailyBalance> find(String merchantId, LocalDate businessDate, String currency) {
        return projectionPort.find(merchantId, businessDate, currency);
    }
}
