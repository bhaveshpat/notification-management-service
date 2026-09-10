package com.meetbhavesh.notification.domain.repository;

import com.meetbhavesh.notification.domain.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, String> {
    List<AuditEvent> findByNotificationIdOrderByOccurredAtAsc(String notificationId);
}
