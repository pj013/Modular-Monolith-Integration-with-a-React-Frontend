package edu.cit.aaron.notification;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository repository;

    NotificationController(NotificationRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<NotificationDto> listNotifications() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(n -> new NotificationDto(n.getNotificationId(), n.getMessage(), n.getCreatedAt()))
                .toList();
    }
}
