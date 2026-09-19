package br.com.verx.cashflow.transaction.adapters.out.observability;

import br.com.verx.cashflow.transaction.ports.out.OutboxPort;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class OutboxMetrics {

    private final OutboxPort outbox;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicReference<Double> oldestAgeSeconds = new AtomicReference<>(0.0);

    public OutboxMetrics(OutboxPort outbox, MeterRegistry meterRegistry) {
        this.outbox = outbox;
        Gauge.builder("cashflow.outbox.pending", pending, AtomicLong::doubleValue)
                .description("Quantidade de eventos Outbox ainda não publicados")
                .register(meterRegistry);
        Gauge.builder("cashflow.outbox.oldest.age.seconds", oldestAgeSeconds, value -> value.get())
                .description("Idade em segundos do evento Outbox pendente mais antigo")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${cashflow.metrics.refresh-ms:10000}")
    public void refresh() {
        pending.set(outbox.countPending());
        double age = outbox.oldestPendingCreatedAt()
                .map(createdAt -> Math.max(0, Duration.between(createdAt, Instant.now()).toSeconds()))
                .map(Long::doubleValue)
                .orElse(0.0);
        oldestAgeSeconds.set(age);
    }
}
