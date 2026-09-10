package com.meetbhavesh.notification.domain;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * notification-domain is a plain library module (no @SpringBootApplication
 * of its own), so @DataJpaTest needs a minimal Spring Boot configuration to
 * bootstrap against. Test-only -- mirrors the scanning config duplicated
 * (necessarily) in NotificationApiApplication / DeliveryWorkerApplication.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = "com.meetbhavesh.notification.domain.entity")
@EnableJpaRepositories(basePackages = "com.meetbhavesh.notification.domain.repository")
public class TestJpaConfig {
}
