package com.paystream.payment.outbox;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAtAsc(Limit limit);

    long countByPublishedAtIsNull();

    @Transactional
    @Modifying
    @Query("update OutboxEvent e set e.publishedAt = :at, e.attempts = e.attempts + 1 where e.id in :ids")
    int markPublished(Collection<UUID> ids, Instant at);

    @Transactional
    @Modifying
    @Query("update OutboxEvent e set e.attempts = e.attempts + 1 where e.id in :ids")
    int recordFailedAttempt(Collection<UUID> ids);
}
