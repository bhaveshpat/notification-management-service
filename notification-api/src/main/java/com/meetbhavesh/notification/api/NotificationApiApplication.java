package com.meetbhavesh.notification.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * See config.JpaConfig for why entity/repository scanning is configured
 * there instead of directly on this class (slice-test compatibility).
 */
@SpringBootApplication
public class NotificationApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationApiApplication.class, args);
    }
}
