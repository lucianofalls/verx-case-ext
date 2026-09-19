package br.com.verx.cashflow.consolidation.adapters.in.messaging;

import br.com.verx.cashflow.consolidation.application.model.FinancialTransactionRecorded.TransactionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CloudEventFinancialTransactionRecordedMapperTest {

    private final CloudEventFinancialTransactionRecordedMapper mapper =
            new CloudEventFinancialTransactionRecordedMapper(new ObjectMapper());

    @Test
    void deveMapearEventoValido() {
        var event = mapper.fromJson(eventJson(
                "com.verx.cashflow.FinancialTransactionRecorded.v1",
                "/transaction-service",
                "CREDIT"));

        assertEquals("MERCHANT-001", event.merchantId());
        assertEquals(TransactionType.CREDIT, event.transactionType());
        assertEquals("10.0000", event.amount().toPlainString());
    }

    @Test
    void deveRejeitarVersaoDesconhecida() {
        assertThrows(IllegalArgumentException.class, () -> mapper.fromJson(eventJson(
                "com.verx.cashflow.FinancialTransactionRecorded.v99",
                "/transaction-service",
                "CREDIT")));
    }

    @Test
    void deveRejeitarSourceNaoConfiavel() {
        assertThrows(IllegalArgumentException.class, () -> mapper.fromJson(eventJson(
                "com.verx.cashflow.FinancialTransactionRecorded.v1",
                "/unknown-service",
                "CREDIT")));
    }

    @Test
    void deveRejeitarTransactionTypeForaDoContratoInclusiveLowercase() {
        assertThrows(IllegalArgumentException.class, () -> mapper.fromJson(eventJson(
                "com.verx.cashflow.FinancialTransactionRecorded.v1",
                "/transaction-service",
                "credit")));
    }

    @Test
    void deveRejeitarMaisDeQuatroCasasDecimais() {
        String payload = eventJson(
                "com.verx.cashflow.FinancialTransactionRecorded.v1",
                "/transaction-service",
                "CREDIT").replace("10.0000", "10.00001");

        assertThrows(IllegalArgumentException.class, () -> mapper.fromJson(payload));
    }

    @Test
    void deveRejeitarAmountSemCasasDecimaisConformeAsyncApi() {
        String payload = eventJson(
                "com.verx.cashflow.FinancialTransactionRecorded.v1",
                "/transaction-service",
                "CREDIT").replace("10.0000", "10");

        assertThrows(IllegalArgumentException.class, () -> mapper.fromJson(payload));
    }

    @Test
    void deveRejeitarAmountAcimaDaPrecisaoNumeric19_4() {
        String payload = eventJson(
                "com.verx.cashflow.FinancialTransactionRecorded.v1",
                "/transaction-service",
                "CREDIT").replace("10.0000", "1234567890123456.0000");

        assertThrows(IllegalArgumentException.class, () -> mapper.fromJson(payload));
    }

    private String eventJson(String type, String source, String transactionType) {
        return """
                {
                  "specversion":"1.0",
                  "id":"00000000-0000-0000-0000-000000000001",
                  "source":"%s",
                  "type":"%s",
                  "time":"2026-09-18T13:00:00Z",
                  "datacontenttype":"application/json",
                  "data":{
                    "transactionId":"00000000-0000-0000-0000-000000000002",
                    "merchantId":"MERCHANT-001",
                    "transactionType":"%s",
                    "amount":"10.0000",
                    "currency":"BRL",
                    "businessDate":"2026-09-18"
                  }
                }
                """.formatted(source, type, transactionType);
    }
}
