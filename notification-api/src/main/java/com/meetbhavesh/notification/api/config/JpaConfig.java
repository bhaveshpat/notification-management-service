package com.meetbhavesh.notification.api.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Separated out from NotificationApiApplication deliberately: @WebMvcTest
 * (and other slice tests) process annotations declared directly on the main
 * @SpringBootApplication class regardless of slice restrictions, so
 * @EnableJpaRepositories there tries to build JPA repository beans in a
 * context with no DataSource/EntityManagerFactory and fails with
 * NoSuchBeanDefinitionException. As its own plain @Configuration bean,
 * slice tests correctly exclude it (it's not a web-layer type), while the
 * full application context still picks it up via normal component scanning
 * (this package is a sub-package of the app's base package).
 */
@Configuration
@EntityScan(basePackages = "com.meetbhavesh.notification.domain.entity")
@EnableJpaRepositories(basePackages = "com.meetbhavesh.notification.domain.repository")
public class JpaConfig {
}
