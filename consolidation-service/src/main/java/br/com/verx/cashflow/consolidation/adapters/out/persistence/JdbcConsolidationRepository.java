package br.com.verx.cashflow.consolidation.adapters.out.persistence;

import br.com.verx.cashflow.consolidation.application.model.DailyBalance;
import br.com.verx.cashflow.consolidation.application.model.FinancialTransactionRecorded;
import br.com.verx.cashflow.consolidation.ports.out.ConsolidationProjectionPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcConsolidationRepository implements ConsolidationProjectionPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcConsolidationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean registerEvent(UUID eventId) {
        return jdbcTemplate.update(
                "INSERT INTO processed_event (event_id) VALUES (?) ON CONFLICT (event_id) DO NOTHING",
                eventId) == 1;
    }

    @Override
    public void apply(FinancialTransactionRecorded event) {
        String transactionType = event.transactionType().name();

        jdbcTemplate.update("""
                INSERT INTO daily_balance
                (merchant_id, business_date, currency, total_credits, total_debits, balance,
                 last_processed_event_id, last_event_at, updated_at)
                VALUES (?, ?, ?,
                        CASE WHEN ? = 'CREDIT' THEN ? ELSE 0 END,
                        CASE WHEN ? = 'DEBIT' THEN ? ELSE 0 END,
                        CASE WHEN ? = 'CREDIT' THEN ? ELSE -? END,
                        ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (merchant_id, business_date, currency) DO UPDATE SET
                    total_credits = daily_balance.total_credits + EXCLUDED.total_credits,
                    total_debits = daily_balance.total_debits + EXCLUDED.total_debits,
                    balance = daily_balance.balance + EXCLUDED.balance,
                    last_processed_event_id = EXCLUDED.last_processed_event_id,
                    last_event_at = EXCLUDED.last_event_at,
                    updated_at = CURRENT_TIMESTAMP
                """,
                event.merchantId(), event.businessDate(), event.currency(),
                transactionType, event.amount(),
                transactionType, event.amount(),
                transactionType, event.amount(), event.amount(),
                event.eventId(), java.sql.Timestamp.from(event.eventAt()));
    }

    @Override
    public Optional<DailyBalance> find(String merchantId, LocalDate businessDate, String currency) {
        return jdbcTemplate.query("""
                SELECT merchant_id, business_date, currency, total_credits, total_debits, balance,
                       last_processed_event_id, last_event_at, updated_at
                FROM daily_balance
                WHERE merchant_id = ? AND business_date = ? AND currency = ?
                """, this::map, merchantId, businessDate, currency).stream().findFirst();
    }

    private DailyBalance map(ResultSet resultSet, int rowNum) throws SQLException {
        return new DailyBalance(
                resultSet.getString("merchant_id"),
                resultSet.getDate("business_date").toLocalDate(),
                resultSet.getString("currency"),
                resultSet.getBigDecimal("total_credits"),
                resultSet.getBigDecimal("total_debits"),
                resultSet.getBigDecimal("balance"),
                resultSet.getTimestamp("updated_at").toInstant());
    }
}
