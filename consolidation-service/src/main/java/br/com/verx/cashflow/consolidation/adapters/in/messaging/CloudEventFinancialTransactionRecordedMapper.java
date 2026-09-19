package br.com.verx.cashflow.consolidation.adapters.in.messaging;

import br.com.verx.cashflow.consolidation.application.model.FinancialTransactionRecorded;
import br.com.verx.cashflow.consolidation.application.model.FinancialTransactionRecorded.TransactionType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Component
public class CloudEventFinancialTransactionRecordedMapper {

    static final String SUPPORTED_SPEC_VERSION = "1.0";
    static final String SUPPORTED_SOURCE = "/transaction-service";
    static final String SUPPORTED_TYPE = "com.verx.cashflow.FinancialTransactionRecorded.v1";
    static final String SUPPORTED_CONTENT_TYPE = "application/json";

    private final ObjectMapper objectMapper;

    public CloudEventFinancialTransactionRecordedMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FinancialTransactionRecorded fromJson(String cloudEventJson) {
        try {
            JsonNode envelope = objectMapper.readTree(cloudEventJson);
            validateEnvelope(envelope);

            JsonNode data = envelope.path("data");
            UUID eventId = UUID.fromString(required(envelope, "id"));
            UUID transactionId = UUID.fromString(required(data, "transactionId"));
            String merchantId = required(data, "merchantId");
            LocalDate businessDate = LocalDate.parse(required(data, "businessDate"));
            TransactionType transactionType = parseTransactionType(required(data, "transactionType"));
            BigDecimal amount = parseAmount(required(data, "amount"));
            String currency = required(data, "currency");
            Instant eventAt = Instant.parse(required(envelope, "time"));

            if (!currency.matches("^[A-Z]{3}$")) {
                throw new IllegalArgumentException("currency inválida: " + currency);
            }

            return new FinancialTransactionRecorded(
                    eventId,
                    transactionId,
                    merchantId,
                    businessDate,
                    transactionType,
                    amount,
                    currency,
                    eventAt);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Evento FinancialTransactionRecorded inválido ou não suportado", exception);
        }
    }

    private void validateEnvelope(JsonNode envelope) {
        requireEquals(envelope, "specversion", SUPPORTED_SPEC_VERSION);
        requireEquals(envelope, "source", SUPPORTED_SOURCE);
        requireEquals(envelope, "type", SUPPORTED_TYPE);
        requireEquals(envelope, "datacontenttype", SUPPORTED_CONTENT_TYPE);

        required(envelope, "id");
        required(envelope, "time");

        if (!envelope.has("data") || !envelope.get("data").isObject()) {
            throw new IllegalArgumentException("Campo obrigatório inválido: data");
        }
    }

    private TransactionType parseTransactionType(String value) {
        try {
            return TransactionType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("transactionType não suportado: " + value, exception);
        }
    }

    private BigDecimal parseAmount(String value) {
        if (!value.matches("^\\d{1,15}\\.\\d{4}$")) {
            throw new IllegalArgumentException("amount fora do contrato canônico NUMERIC(19,4): " + value);
        }

        BigDecimal amount = new BigDecimal(value);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount deve ser maior que zero");
        }
        if (amount.scale() != 4) {
            throw new IllegalArgumentException("amount do evento deve ter exatamente 4 casas decimais");
        }
        if (amount.precision() > 19 || amount.precision() - amount.scale() > 15) {
            throw new IllegalArgumentException("amount excede NUMERIC(19,4): " + value);
        }
        return amount;
    }

    private void requireEquals(JsonNode node, String field, String expected) {
        String actual = required(node, field);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(
                    "Campo " + field + " não suportado. Esperado=" + expected + ", recebido=" + actual);
        }
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Campo obrigatório ausente: " + field);
        }
        return value;
    }
}
