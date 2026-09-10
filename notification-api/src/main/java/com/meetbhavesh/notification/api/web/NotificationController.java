package com.meetbhavesh.notification.api.web;

import com.meetbhavesh.notification.api.dto.NotificationStatusResponse;
import com.meetbhavesh.notification.api.dto.NotificationSubmitRequest;
import com.meetbhavesh.notification.api.dto.NotificationSubmitResponse;
import com.meetbhavesh.notification.api.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<NotificationSubmitResponse> submit(@Valid @RequestBody NotificationSubmitRequest request) {
        NotificationSubmitResponse response = notificationService.submit(request);
        return ResponseEntity.created(URI.create("/notifications/" + response.notificationId())).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationStatusResponse> getStatus(@PathVariable("id") String id) {
        return ResponseEntity.ok(notificationService.getStatus(id));
    }
}
