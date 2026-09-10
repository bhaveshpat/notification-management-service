package com.meetbhavesh.notification.worker.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * See notification-api's config.JpaConfig for why this is a separate
 * @Configuration bean rather than annotated directly on
 * DeliveryWorkerApplication (slice-test compatibility).
 */
@Configuration
@EntityScan(basePackages = "com.meetbhavesh.notification.domain.entity")
@EnableJpaRepositories(basePackages = "com.meetbhavesh.notification.domain.repository")
public class JpaConfig {
}
