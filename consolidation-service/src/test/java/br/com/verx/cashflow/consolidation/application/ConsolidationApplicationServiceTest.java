package br.com.verx.cashflow.consolidation.application;

import br.com.verx.cashflow.consolidation.application.model.FinancialTransactionRecorded;
import br.com.verx.cashflow.consolidation.application.model.FinancialTransactionRecorded.TransactionType;
import br.com.verx.cashflow.consolidation.ports.out.ConsolidationProjectionPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import br.com.verx.cashflow.consolidation.adapters.out.observability.ConsolidationMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsolidationApplicationServiceTest {

    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Mock
    private ConsolidationProjectionPort projectionPort;

    @Test
    void deveRegistrarEventoEAtualizarProjecao() {
        FinancialTransactionRecorded event = event();
        when(projectionPort.registerEvent(EVENT_ID)).thenReturn(true);

        service().process(event);

        verify(projectionPort).apply(event);
    }

    @Test
    void deveIgnorarEventoDuplicadoAntesDeAtualizarSaldo() {
        FinancialTransactionRecorded event = event();
        when(projectionPort.registerEvent(EVENT_ID)).thenReturn(false);

        service().process(event);

        verify(projectionPort, never()).apply(event);
    }

    private ConsolidationApplicationService service() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        return new ConsolidationApplicationService(
                projectionPort,
                registry,
                new ConsolidationMetrics(registry));
    }

    private FinancialTransactionRecorded event() {
        return new FinancialTransactionRecorded(
                EVENT_ID,
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "MERCHANT-001",
                LocalDate.of(2026, 9, 18),
                TransactionType.CREDIT,
                new BigDecimal("10.0000"),
                "BRL",
                Instant.parse("2026-09-18T13:00:00Z"));
    }
}
