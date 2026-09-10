package com.meetbhavesh.notification.domain.repository;

import com.meetbhavesh.notification.domain.entity.DeliveryTask;
import com.meetbhavesh.notification.domain.enums.DeliveryTaskStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface DeliveryTaskRepository extends JpaRepository<DeliveryTask, String> {

    List<DeliveryTask> findByNotificationId(String notificationId);

    /**
     * Candidate rows for the worker to claim: QUEUED and due (nextAttemptAt
     * unset, or in the past).
     *
     * NOTE (documented limitation): this is a plain SELECT, not a locking
     * SELECT ... FOR UPDATE SKIP LOCKED claim. It is correct for a single
     * delivery-worker instance (the prototype's assumption) but is NOT safe
     * for multiple concurrent worker instances, which could double-claim the
     * same row. See docs/ARCHITECTURE.md / DECISIONS.md.
     */
    @Query("select t from DeliveryTask t "
            + "where t.status = :status "
            + "and (t.nextAttemptAt is null or t.nextAttemptAt <= :now) "
            + "order by t.createdAt asc")
    List<DeliveryTask> findDueForProcessing(
            @Param("status") DeliveryTaskStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );
}
