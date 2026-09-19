package br.com.verx.cashflow.transaction.adapters.out.persistence;

import br.com.verx.cashflow.transaction.ports.out.OutboxPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

@Repository
public class JdbcOutboxAdapter implements OutboxPort {
    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboxAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public List<PendingEvent> findPending(int limit) {
        UUID claimToken = UUID.randomUUID();
        return jdbcTemplate.query("""
                WITH candidates AS (
                    SELECT id
                    FROM outbox_event
                    WHERE published_at IS NULL
                      AND retry_count < 10
                      AND next_attempt_at <= CURRENT_TIMESTAMP
                      AND (claimed_at IS NULL OR claimed_at < CURRENT_TIMESTAMP - INTERVAL '30 seconds')
                    ORDER BY created_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE outbox_event event
                SET claim_token = ?, claimed_at = CURRENT_TIMESTAMP
                FROM candidates
                WHERE event.id = candidates.id
                RETURNING event.id, event.aggregate_id, event.event_type, event.event_version,
                          event.payload::text, event.created_at, event.retry_count, event.claim_token
                """, this::map, limit, claimToken);
    }

    @Override
    @Transactional
    public void markPublished(UUID id, UUID claimToken) {
        jdbcTemplate.update("""
                UPDATE outbox_event
                SET published_at = CURRENT_TIMESTAMP, claim_token = NULL, claimed_at = NULL
                WHERE id = ? AND claim_token = ?
                """, id, claimToken);
    }

    @Override
    @Transactional
    public void markFailed(UUID id, UUID claimToken) {
        jdbcTemplate.update("""
                UPDATE outbox_event
                SET retry_count = retry_count + 1,
                    next_attempt_at = CURRENT_TIMESTAMP +
                        (POWER(2, LEAST(retry_count + 1, 6)) * INTERVAL '1 second'),
                    claim_token = NULL,
                    claimed_at = NULL
                WHERE id = ? AND claim_token = ?
                """, id, claimToken);
    }

    @Override
    public long countPending() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_event WHERE published_at IS NULL",
                Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public Optional<Instant> oldestPendingCreatedAt() {
        java.sql.Timestamp timestamp = jdbcTemplate.queryForObject(
                "SELECT MIN(created_at) FROM outbox_event WHERE published_at IS NULL",
                java.sql.Timestamp.class);
        return Optional.ofNullable(timestamp).map(java.sql.Timestamp::toInstant);
    }

    private PendingEvent map(ResultSet resultSet, int rowNum) throws SQLException {
        return new PendingEvent(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("aggregate_id", UUID.class),
                resultSet.getString("event_type"),
                resultSet.getInt("event_version"),
                resultSet.getString("payload"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getInt("retry_count"),
                resultSet.getObject("claim_token", UUID.class));
    }
}
