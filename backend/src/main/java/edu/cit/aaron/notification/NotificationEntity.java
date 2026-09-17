package edu.cit.aaron.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
class NotificationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "message", nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected NotificationEntity() {
        // required by JPA
    }

    NotificationEntity(String message, LocalDateTime createdAt) {
        this.message = message;
        this.createdAt = createdAt;
    }

    Long getNotificationId() {
        return notificationId;
    }

    String getMessage() {
        return message;
    }

    LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
