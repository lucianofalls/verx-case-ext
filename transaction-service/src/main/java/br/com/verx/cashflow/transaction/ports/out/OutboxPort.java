package br.com.verx.cashflow.transaction.ports.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxPort {
    record PendingEvent(UUID id, UUID aggregateId, String eventType, int eventVersion,
                        String payload, Instant createdAt, int retryCount, UUID claimToken) {
    }

    List<PendingEvent> findPending(int limit);

    void markPublished(UUID id, UUID claimToken);

    void markFailed(UUID id, UUID claimToken);

    long countPending();

    Optional<Instant> oldestPendingCreatedAt();
}
