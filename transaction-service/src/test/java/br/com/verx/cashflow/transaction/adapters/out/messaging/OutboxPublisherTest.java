package br.com.verx.cashflow.transaction.adapters.out.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxPublisherTest {

    @Test
    void deveAceitarSomenteAckSemMensagemDevolvida() {
        CorrelationData correlationData = mock(CorrelationData.class);
        CorrelationData.Confirm confirmation = mock(CorrelationData.Confirm.class);

        when(confirmation.isAck()).thenReturn(true);
        when(correlationData.getReturned()).thenReturn(null);

        assertDoesNotThrow(() -> OutboxPublisher.assertDelivered(correlationData, confirmation));
    }

    @Test
    void deveRejeitarNackDoBroker() {
        CorrelationData correlationData = mock(CorrelationData.class);
        CorrelationData.Confirm confirmation = mock(CorrelationData.Confirm.class);

        when(confirmation.isAck()).thenReturn(false);
        when(confirmation.getReason()).thenReturn("broker nack");

        assertThrows(IllegalStateException.class,
                () -> OutboxPublisher.assertDelivered(correlationData, confirmation));
    }

    @Test
    void deveRejeitarAckQuandoMensagemFoiDevolvidaSemRota() {
        CorrelationData correlationData = mock(CorrelationData.class);
        CorrelationData.Confirm confirmation = mock(CorrelationData.Confirm.class);

        when(confirmation.isAck()).thenReturn(true);
        when(correlationData.getReturned()).thenReturn(mock(ReturnedMessage.class));

        assertThrows(IllegalStateException.class,
                () -> OutboxPublisher.assertDelivered(correlationData, confirmation));
    }
}
