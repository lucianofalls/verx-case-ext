package br.com.verx.cashflow.transaction.adapters.out.persistence;

import br.com.verx.cashflow.transaction.domain.FinancialTransaction;
import br.com.verx.cashflow.transaction.domain.FinancialTransactionRecorded;
import br.com.verx.cashflow.transaction.domain.Money;
import br.com.verx.cashflow.transaction.domain.TransactionType;
import br.com.verx.cashflow.transaction.ports.out.TransactionPersistencePort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcTransactionPersistenceAdapter implements TransactionPersistencePort {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcTransactionPersistenceAdapter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<FinancialTransaction> findById(UUID transactionId) {
        return queryOne("SELECT * FROM financial_transaction WHERE transaction_id = ?", transactionId);
    }

    @Override
    public Optional<FinancialTransaction> findByIdempotencyKey(String idempotencyKey) {
        return queryOne("SELECT * FROM financial_transaction WHERE idempotency_key = ?", idempotencyKey);
    }

    @Override
    public Optional<FinancialTransaction> findReversalByOriginalTransactionId(UUID originalTransactionId) {
        return queryOne(
                "SELECT * FROM financial_transaction WHERE reversal_of_transaction_id = ?",
                originalTransactionId);
    }

    @Override
    public TransactionPage findByMerchant(String merchantId, LocalDate from, LocalDate to, int page, int size) {
        String filter = " WHERE merchant_id = ?";
        List<Object> arguments = new java.util.ArrayList<>(List.of(merchantId));
        if (from != null) {
            filter += " AND business_date >= ?";
            arguments.add(from);
        }
        if (to != null) {
            filter += " AND business_date <= ?";
            arguments.add(to);
        }
        List<FinancialTransaction> content = jdbcTemplate.query(
                "SELECT * FROM financial_transaction" + filter +
                        " ORDER BY occurred_at DESC, transaction_id DESC LIMIT ? OFFSET ?",
                this::map,
                append(arguments, size, page * size));
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM financial_transaction" + filter,
                Long.class, arguments.toArray());
        return new TransactionPage(content, page, size, total == null ? 0 : total);
    }

    private Object[] append(List<Object> arguments, Object... values) {
        List<Object> result = new java.util.ArrayList<>(arguments);
        result.addAll(List.of(values));
        return result.toArray();
    }

    @Override
    @Transactional
    public void saveTransactionAndOutbox(FinancialTransaction transaction, FinancialTransactionRecorded event) {
        jdbcTemplate.update("""
                INSERT INTO financial_transaction
                (transaction_id, merchant_id, transaction_type, amount, currency, description,
                 occurred_at, business_date, idempotency_key, created_at, reversal_of_transaction_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                transaction.transactionId(), transaction.merchantId(), transaction.type().name(),
                transaction.amount().amount(), transaction.amount().currency(), transaction.description(),
                Timestamp.from(transaction.occurredAt()), transaction.businessDate(),
                transaction.idempotencyKey(), Timestamp.from(transaction.createdAt()),
                transaction.reversalOfTransactionId());

        jdbcTemplate.update("""
                INSERT INTO outbox_event
                (id, aggregate_id, event_type, event_version, payload)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb))
                """,
                UUID.randomUUID(), transaction.transactionId(),
                "FinancialTransactionRecorded", 1, serializeEvent(event));
    }

    private String serializeEvent(FinancialTransactionRecorded event) {
        try {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("transactionId", event.transactionId());
            payload.put("merchantId", event.merchantId());
            payload.put("transactionType", event.transactionType().name());
            payload.put("amount", event.amount().toContractString());
            payload.put("currency", event.amount().currency());
            payload.put("businessDate", event.businessDate().toString());
            payload.put("occurredAt", event.occurredAt().toString());
            if (event.reversalOfTransactionId() != null) {
                payload.put("reversalOfTransactionId", event.reversalOfTransactionId());
            }
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Não foi possível serializar FinancialTransactionRecorded", exception);
        }
    }

    private Optional<FinancialTransaction> queryOne(String sql, Object... args) {
        List<FinancialTransaction> result = jdbcTemplate.query(sql, this::map, args);
        return result.stream().findFirst();
    }

    private FinancialTransaction map(ResultSet resultSet, int rowNum) throws SQLException {
        return FinancialTransaction.reconstitute(
                resultSet.getObject("transaction_id", UUID.class),
                resultSet.getString("merchant_id"),
                TransactionType.valueOf(resultSet.getString("transaction_type")),
                Money.of(resultSet.getBigDecimal("amount"), resultSet.getString("currency")),
                resultSet.getString("description"),
                resultSet.getTimestamp("occurred_at").toInstant(),
                resultSet.getDate("business_date").toLocalDate(),
                resultSet.getString("idempotency_key"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getObject("reversal_of_transaction_id", UUID.class));
    }
}
