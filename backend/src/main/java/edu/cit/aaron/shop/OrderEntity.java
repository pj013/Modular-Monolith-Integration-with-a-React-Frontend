package edu.cit.aaron.shop;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected OrderEntity() {
        // required by JPA
    }

    OrderEntity(String productId, int quantity, String status, String reason, LocalDateTime createdAt) {
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
        this.reason = reason;
        this.createdAt = createdAt;
    }

    Long getOrderId() {
        return orderId;
    }

    String getProductId() {
        return productId;
    }

    int getQuantity() {
        return quantity;
    }

    String getStatus() {
        return status;
    }

    String getReason() {
        return reason;
    }

    LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
