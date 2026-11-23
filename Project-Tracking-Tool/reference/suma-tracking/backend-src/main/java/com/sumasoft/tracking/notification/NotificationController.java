package com.sumasoft.tracking.notification;

import com.sumasoft.tracking.notification.NotificationService.NotificationRow;
import com.sumasoft.tracking.notification.NotificationService.UnreadCount;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The signed-in employee's own notifications (Step 37).
 *   GET  /api/notifications?unreadOnly=false
 *   GET  /api/notifications/unread-count
 *   POST /api/notifications/{id}/read
 *   POST /api/notifications/read-all
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public List<NotificationRow> mine(@RequestParam(defaultValue = "false") boolean unreadOnly) {
        return service.mine(unreadOnly);
    }

    @GetMapping("/unread-count")
    public UnreadCount unreadCount() {
        return service.unreadCount();
    }

    @PostMapping("/{id}/read")
    public UnreadCount read(@PathVariable Long id) {
        service.markRead(id);
        return service.unreadCount();
    }

    @PostMapping("/read-all")
    public UnreadCount readAll() {
        return service.markAllRead();
    }
}