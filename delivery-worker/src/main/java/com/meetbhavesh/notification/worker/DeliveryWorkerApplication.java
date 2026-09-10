package com.meetbhavesh.notification.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * See config.JpaConfig for why entity/repository scanning is configured
 * there instead of directly on this class (slice-test compatibility).
 */
@SpringBootApplication
@EnableScheduling
public class DeliveryWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeliveryWorkerApplication.class, args);
    }
}
