package br.com.verx.cashflow.consolidation.adapters.out.observability;

import br.com.verx.cashflow.consolidation.adapters.out.messaging.RabbitMessagingConfiguration;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class RabbitQueueMetrics {

    private final RabbitTemplate rabbitTemplate;
    private final AtomicLong mainQueueDepth = new AtomicLong();
    private final AtomicLong deadLetterQueueDepth = new AtomicLong();

    public RabbitQueueMetrics(RabbitTemplate rabbitTemplate, MeterRegistry meterRegistry) {
        this.rabbitTemplate = rabbitTemplate;

        Gauge.builder("cashflow.rabbitmq.queue.depth", mainQueueDepth, AtomicLong::doubleValue)
                .tag("queue", RabbitMessagingConfiguration.QUEUE)
                .description("Mensagens prontas na fila principal")
                .register(meterRegistry);

        Gauge.builder("cashflow.rabbitmq.queue.depth", deadLetterQueueDepth, AtomicLong::doubleValue)
                .tag("queue", RabbitMessagingConfiguration.DEAD_LETTER_QUEUE)
                .description("Mensagens prontas na dead-letter queue")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${cashflow.metrics.refresh-ms:10000}")
    public void refresh() {
        updateDepth(RabbitMessagingConfiguration.QUEUE, mainQueueDepth);
        updateDepth(RabbitMessagingConfiguration.DEAD_LETTER_QUEUE, deadLetterQueueDepth);
    }

    private void updateDepth(String queue, AtomicLong target) {
        Long count = rabbitTemplate.execute(channel ->
                (long) channel.queueDeclarePassive(queue).getMessageCount());
        if (count != null) {
            target.set(count);
        }
    }
}
