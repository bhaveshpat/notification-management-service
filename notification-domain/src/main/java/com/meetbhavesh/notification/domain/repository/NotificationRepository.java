package com.meetbhavesh.notification.domain.repository;

import com.meetbhavesh.notification.domain.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, String> {
    Optional<Notification> findByIdempotencyKey(String idempotencyKey);
}
