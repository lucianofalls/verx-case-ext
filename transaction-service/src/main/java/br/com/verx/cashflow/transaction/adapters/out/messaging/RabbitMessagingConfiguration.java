package br.com.verx.cashflow.transaction.adapters.out.messaging;

import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class RabbitMessagingConfiguration {
    public static final String EXCHANGE = "cashflow.events";
    public static final String QUEUE = "financial.transactions.recorded";
    public static final String ROUTING_KEY = "financial.transaction.recorded";
    public static final String DEAD_LETTER_EXCHANGE = "cashflow.events.dlx";
    public static final String DEAD_LETTER_QUEUE = "financial.transactions.recorded.dlq";

    @Bean
    DirectExchange cashflowExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue financialTransactionsQueue() {
        return QueueBuilder.durable(QUEUE)
            .deadLetterExchange(DEAD_LETTER_EXCHANGE)
            .deadLetterRoutingKey(ROUTING_KEY)
            .build();
    }

    @Bean
    Binding financialTransactionsBinding(@Qualifier("financialTransactionsQueue") Queue financialTransactionsQueue,
                                         DirectExchange cashflowExchange) {
        return BindingBuilder.bind(financialTransactionsQueue)
                .to(cashflowExchange)
                .with(ROUTING_KEY);
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMandatory(true);
        return template;
    }

    @Bean
    TopicExchange deadLetterExchange() {
        return new TopicExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    Binding deadLetterBinding(@Qualifier("deadLetterQueue") Queue deadLetterQueue,
                              TopicExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(ROUTING_KEY);
    }
}
