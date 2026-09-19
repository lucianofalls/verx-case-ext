package br.com.verx.cashflow.consolidation.adapters.out.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ConsolidationMetrics {

    private final AtomicReference<Double> lagSeconds = new AtomicReference<>(0.0);

    public ConsolidationMetrics(MeterRegistry meterRegistry) {
        Gauge.builder("cashflow.consolidation.lag.seconds", lagSeconds, value -> value.get())
                .description("Atraso entre o timestamp do CloudEvent e a aplicação no read model")
                .register(meterRegistry);
    }

    public void recordProcessedEvent(Instant eventAt) {
        lagSeconds.set((double) Math.max(0, Duration.between(eventAt, Instant.now()).toSeconds()));
    }
}
