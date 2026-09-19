package br.com.verx.cashflow.transaction.adapters.out.persistence;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class JdbcOutboxAdapterIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static JdbcOutboxAdapter adapter;

    @BeforeAll
    static void setUp() {
        JdbcTemplate adminTemplate = new JdbcTemplate(new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));
        adminTemplate.execute("CREATE SCHEMA transactions");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
            POSTGRES.getJdbcUrl() + "&currentSchema=transactions",
            POSTGRES.getUsername(), POSTGRES.getPassword()));
        jdbcTemplate.execute("""
                CREATE TABLE outbox_event (
                    id UUID PRIMARY KEY,
                    aggregate_id UUID NOT NULL,
                    event_type VARCHAR(255) NOT NULL,
                    event_version INTEGER NOT NULL,
                    payload JSONB NOT NULL,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    published_at TIMESTAMPTZ,
                    retry_count INTEGER NOT NULL DEFAULT 0,
                    claim_token UUID,
                    claimed_at TIMESTAMPTZ,
                    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO outbox_event (id, aggregate_id, event_type, event_version, payload)
                VALUES (?, ?, 'FinancialTransactionRecorded', 1, CAST(? AS jsonb))
                """, UUID.randomUUID(), UUID.randomUUID(), "{}");
        adapter = new JdbcOutboxAdapter(jdbcTemplate);
    }

    @Test
    void doisPublishersNaoReivindicamOMesmoEvento() {
        var firstClaim = adapter.findPending(1);
        var secondClaim = adapter.findPending(1);

        assertThat(firstClaim).hasSize(1);
        assertThat(secondClaim).isEmpty();
        assertThat(firstClaim.getFirst().claimToken()).isNotNull();
    }
}
