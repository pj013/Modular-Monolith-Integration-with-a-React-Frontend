package edu.cit.aaron.shop;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "reason")
    private String reason;

    @Column(name = "external_reference", unique = true)
    private String externalReference;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItemEntity> items = new ArrayList<>();

    protected OrderEntity() {
        // required by JPA
    }

    OrderEntity(String status, String reason, LocalDateTime createdAt) {
        this(status, reason, createdAt, null);
    }

    OrderEntity(String status, String reason, LocalDateTime createdAt, String externalReference) {
        this.status = status;
        this.reason = reason;
        this.createdAt = createdAt;
        this.externalReference = externalReference;
    }

    void addItem(OrderItemEntity item) {
        item.setOrder(this);
        items.add(item);
    }

    Long getOrderId() {
        return orderId;
    }

    String getStatus() {
        return status;
    }

    void setStatus(String status) {
        this.status = status;
    }

    String getReason() {
        return reason;
    }

    void setReason(String reason) {
        this.reason = reason;
    }

    String getExternalReference() {
        return externalReference;
    }

    LocalDateTime getCreatedAt() {
        return createdAt;
    }

    List<OrderItemEntity> getItems() {
        return items;
    }
}
