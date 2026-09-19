package br.com.verx.cashflow.transaction.adapters.out.messaging;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RabbitMqContainerSmokeTest {
    @Container
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4.1-management-alpine");

    @Test
    void brokerDeveAceitarConexaoAmqp() {
        assertThat(RABBITMQ.isRunning()).isTrue();
        assertThat(RABBITMQ.getAmqpUrl()).startsWith("amqp://");
    }
}
