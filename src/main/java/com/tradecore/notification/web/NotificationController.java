package com.tradecore.notification.web;

import com.tradecore.notification.model.NotificationResponse;
import com.tradecore.notification.service.NotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** REST polling endpoint. Replace userId query parameter with authenticated identity when auth is integrated. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> getNotifications(@RequestParam String userId) {
        if (userId == null || userId.isBlank() || userId.length() > 64) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "userId must contain 1 to 64 characters");
        }
        return notificationService.getNotificationsForUser(userId.trim());
    }
}
