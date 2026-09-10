package com.meetbhavesh.notification.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Shared JPA entities/repositories live in com.meetbhavesh.notification.domain,
 * a sibling package to this one -- not a sub-package -- so Spring Boot's
 * default (main-class-package-based) entity/repository scanning would miss
 * them. Scanned explicitly below.
 */
@SpringBootApplication
@EntityScan(basePackages = "com.meetbhavesh.notification.domain.entity")
@EnableJpaRepositories(basePackages = "com.meetbhavesh.notification.domain.repository")
public class NotificationApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationApiApplication.class, args);
    }
}
