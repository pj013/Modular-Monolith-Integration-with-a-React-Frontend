package edu.cit.aaron.notification;

import java.time.LocalDateTime;

public record NotificationDto(Long notificationId, String message, LocalDateTime createdAt) {
}
