package br.com.verx.cashflow.consolidation.adapters.in.messaging;

import br.com.verx.cashflow.consolidation.application.ConsolidationApplicationService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class FinancialTransactionRecordedListener {

    private static final Logger log = LoggerFactory.getLogger(FinancialTransactionRecordedListener.class);

    private final ConsolidationApplicationService service;
    private final CloudEventFinancialTransactionRecordedMapper mapper;
    private final MeterRegistry meterRegistry;

    public FinancialTransactionRecordedListener(ConsolidationApplicationService service,
                                                CloudEventFinancialTransactionRecordedMapper mapper,
                                                MeterRegistry meterRegistry) {
        this.service = service;
        this.mapper = mapper;
        this.meterRegistry = meterRegistry;
    }

    @RabbitListener(queues = "${cashflow.messaging.queue:financial.transactions.recorded}")
    public void onMessage(String cloudEventJson) {
        try {
            service.process(mapper.fromJson(cloudEventJson));
        } catch (IllegalArgumentException exception) {
            meterRegistry.counter("cashflow.consolidation.events.errors").increment();
            log.warn("business_event=consolidation_event_rejected error={}", exception.getMessage());
            throw exception;
        }
    }
}
