package com.meetbhavesh.notification.api.config;

import org.h2.tools.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.sql.SQLException;

/**
 * Starts an H2 TCP server exposing this JVM's in-memory database so
 * delivery-worker (a separate process) can connect to the SAME in-memory
 * instance instead of getting its own empty one.
 *
 * Dev/assignment-only convenience: disabled entirely under the "postgres"
 * profile, where both services already talk to a real, independently
 * reachable Postgres server. See docs/DECISIONS.md.
 *
 * Operational note: notification-api must be started before delivery-worker
 * so the TCP server is listening by the time the worker tries to connect.
 */
@Configuration
@ConditionalOnProperty(prefix = "notification.h2-server", name = "enabled", havingValue = "true", matchIfMissing = false)
public class H2TcpServerConfig implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(H2TcpServerConfig.class);

    @Value("${notification.h2-server.tcp-port:9092}")
    private String tcpPort;

    private Server server;

    @EventListener(ApplicationReadyEvent.class)
    public void startH2TcpServer() throws SQLException {
        server = Server.createTcpServer(
                "-tcpPort", tcpPort,
                "-tcpAllowOthers",
                "-ifNotExists"
        ).start();
        log.info("H2 TCP server listening on port {} (shares this JVM's in-memory DB with delivery-worker)", tcpPort);
    }

    @Override
    public void destroy() {
        if (server != null) {
            server.stop();
        }
    }
}
