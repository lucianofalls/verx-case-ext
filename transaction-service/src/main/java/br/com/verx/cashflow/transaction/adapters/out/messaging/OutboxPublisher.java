package br.com.verx.cashflow.transaction.adapters.out.messaging;

import br.com.verx.cashflow.transaction.ports.out.OutboxPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxPort outbox;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Tracer tracer;

    public OutboxPublisher(OutboxPort outbox, RabbitTemplate rabbitTemplate, ObjectMapper objectMapper,
                           MeterRegistry meterRegistry, Tracer tracer) {
        this.outbox = outbox;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
    }

    @Scheduled(fixedDelayString = "${cashflow.outbox.fixed-delay-ms:1000}")
    public void publishPendingEvents() {
        var pending = outbox.findPending(50);
        if (!pending.isEmpty()) {
            log.info("business_event=outbox_batch_claimed count={}", pending.size());
        }
        pending.forEach(event -> {
            Span publishSpan = tracer.nextSpan()
                    .name("cashflow.outbox.publish")
                    .tag("messaging.system", "rabbitmq")
                    .tag("messaging.destination.name", RabbitMessagingConfiguration.EXCHANGE)
                    .tag("cashflow.event.id", event.id().toString())
                    .tag("cashflow.aggregate.id", event.aggregateId().toString())
                    .start();
            try (Tracer.SpanInScope ignored = tracer.withSpan(publishSpan)) {
                CorrelationData correlationData = new CorrelationData(event.id().toString());
                rabbitTemplate.convertAndSend(
                        RabbitMessagingConfiguration.EXCHANGE,
                        RabbitMessagingConfiguration.ROUTING_KEY,
                        cloudEvent(event),
                        message -> {
                            message.getMessageProperties().setContentType("application/cloudevents+json");
                            return message;
                        },
                        correlationData);

                CorrelationData.Confirm confirmation = correlationData.getFuture().get(5, TimeUnit.SECONDS);
                assertDelivered(correlationData, confirmation);

                outbox.markPublished(event.id(), event.claimToken());
                meterRegistry.counter("cashflow.outbox.published").increment();
                log.info("business_event=event_published eventId={} aggregateId={} eventType={} retryCount={}",
                        event.id(), event.aggregateId(), event.eventType(), event.retryCount());
            } catch (InterruptedException exception) {
                publishSpan.error(exception);
                Thread.currentThread().interrupt();
                outbox.markFailed(event.id(), event.claimToken());
                meterRegistry.counter("cashflow.outbox.publish.errors").increment();
                log.warn("business_event=event_publish_failed eventId={} aggregateId={} reason=interrupted",
                        event.id(), event.aggregateId());
            } catch (RuntimeException | java.util.concurrent.ExecutionException |
                     java.util.concurrent.TimeoutException exception) {
                publishSpan.error(exception);
                outbox.markFailed(event.id(), event.claimToken());
                meterRegistry.counter("cashflow.outbox.publish.errors").increment();
                log.warn("business_event=event_publish_failed eventId={} aggregateId={} retryCount={} error={}",
                        event.id(), event.aggregateId(), event.retryCount(), exception.getMessage());
            } finally {
                publishSpan.end();
            }
        });
    }

    static void assertDelivered(CorrelationData correlationData, CorrelationData.Confirm confirmation) {
        if (!confirmation.isAck()) {
            throw new IllegalStateException("RabbitMQ rejeitou o evento: " + confirmation.getReason());
        }

        /*
         * Publisher confirm (ACK) significa que o broker recebeu a publicação.
         * Ele NÃO garante que a mensagem foi roteada para uma fila. Com mandatory=true,
         * mensagens sem binding são devolvidas via basic.return e ficam disponíveis em
         * CorrelationData#getReturned(). Somente marcamos a Outbox como publicada quando
         * há ACK e nenhuma devolução.
         */
        if (correlationData.getReturned() != null) {
            throw new IllegalStateException("RabbitMQ devolveu o evento porque nenhuma rota/fila aceitou a mensagem");
        }
    }

    private String cloudEvent(OutboxPort.PendingEvent event) {
        try {
            JsonNode data = objectMapper.readTree(event.payload());
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("specversion", "1.0");
            envelope.put("id", event.id().toString());
            envelope.put("source", "/transaction-service");
            envelope.put("type", "com.verx.cashflow.FinancialTransactionRecorded.v1");
            envelope.put("time", Instant.now().toString());
            envelope.put("datacontenttype", "application/json");
            envelope.set("data", data);
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception exception) {
            throw new IllegalStateException("Não foi possível montar o CloudEvent " + event.id(), exception);
        }
    }
}
