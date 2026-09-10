package com.meetbhavesh.notification.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * See NotificationApiApplication for why entity/repository packages are
 * scanned explicitly (they live in a sibling package, not a sub-package).
 */
@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = "com.meetbhavesh.notification.domain.entity")
@EnableJpaRepositories(basePackages = "com.meetbhavesh.notification.domain.repository")
public class DeliveryWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeliveryWorkerApplication.class, args);
    }
}
