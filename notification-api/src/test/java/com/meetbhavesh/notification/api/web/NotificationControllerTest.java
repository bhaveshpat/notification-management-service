package com.meetbhavesh.notification.api.web;

import com.meetbhavesh.notification.api.dto.NotificationSubmitResponse;
import com.meetbhavesh.notification.api.exception.NotificationNotFoundException;
import com.meetbhavesh.notification.api.service.NotificationService;
import com.meetbhavesh.notification.domain.enums.NotificationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @Test
    void submit_withValidRequest_returns201() throws Exception {
        when(notificationService.submit(any())).thenReturn(
                new NotificationSubmitResponse("abc-123", NotificationStatus.ROUTED, false, Instant.now()));

        String body = """
                {
                  "idempotencyKey": "key-1",
                  "sourceSystem": "order-service",
                  "eventId": "evt-1",
                  "notificationType": "ORDER_SHIPPED",
                  "severity": "MEDIUM",
                  "priority": "NORMAL",
                  "recipients": ["user-1"],
                  "requestedChannels": ["EMAIL"]
                }
                """;

        mockMvc.perform(post("/notifications")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notificationId").value("abc-123"))
                .andExpect(jsonPath("$.status").value("ROUTED"));
    }

    @Test
    void submit_withMissingRequiredField_returns400() throws Exception {
        String body = """
                {
                  "sourceSystem": "order-service",
                  "notificationType": "ORDER_SHIPPED",
                  "severity": "MEDIUM",
                  "priority": "NORMAL",
                  "recipients": ["user-1"],
                  "requestedChannels": ["EMAIL"]
                }
                """;

        mockMvc.perform(post("/notifications")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getStatus_unknownId_returns404() throws Exception {
        when(notificationService.getStatus(eq("missing")))
                .thenThrow(new NotificationNotFoundException("missing"));

        mockMvc.perform(get("/notifications/missing"))
                .andExpect(status().isNotFound());
    }
}
