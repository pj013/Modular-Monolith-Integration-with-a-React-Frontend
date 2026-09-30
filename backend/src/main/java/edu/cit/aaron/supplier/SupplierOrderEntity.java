package edu.cit.aaron.supplier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.UUID;

@SuppressWarnings("unused")
@Entity
@Table(name = "supplier_orders", uniqueConstraints = {
        @UniqueConstraint(name = "uk_supplier_orders_buyer_ref", columnNames = "buyer_ref"),
        @UniqueConstraint(name = "uk_supplier_orders_request_id", columnNames = "request_id")
})
class SupplierOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(name = "buyer_ref", nullable = false, length = 40)
    private String buyerRef;

    @Column(name = "request_id", nullable = false, length = 80)
    private String requestId;

    @Column(name = "po_number")
    private String poNumber;

    @Column(name = "cases", nullable = false)
    private int cases;

    @Column(name = "units", nullable = false)
    private int units;

    @Column(name = "received_units", nullable = false)
    private int receivedUnits;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SupplierOrderStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at")
    private LocalDateTime nextAttemptAt;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected SupplierOrderEntity() {
    }

    SupplierOrderEntity(String productId, int units) {
        this.productId = productId;
        this.units = units;
        this.status = SupplierOrderStatus.PENDING;
        this.nextAttemptAt = LocalDateTime.now();
        String temporaryRef = "TMP-" + UUID.randomUUID();
        this.buyerRef = temporaryRef;
        this.requestId = temporaryRef;
    }

    @PrePersist
    void beforeInsert() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = LocalDateTime.now();
    }

    String getProductId() { return productId; }
    String getBuyerRef() { return buyerRef; }
    String getRequestId() { return requestId; }
    String getPoNumber() { return poNumber; }
    int getUnits() { return units; }
    int getReceivedUnits() { return receivedUnits; }
    SupplierOrderStatus getStatus() { return status; }
    int getAttemptCount() { return attemptCount; }
    void assignReferences() {
        buyerRef = "RO-" + id;
        requestId = buyerRef;
    }

    void prepareRetry(LocalDateTime nextAttemptAt, String error) {
        attemptCount++;
        this.nextAttemptAt = nextAttemptAt;
        lastError = error;
    }

    void recordSubmission(String poNumber, int cases, int receivedUnits, SupplierOrderStatus status) {
        this.poNumber = poNumber;
        this.cases = cases;
        this.receivedUnits = receivedUnits;
        this.status = status;
        this.lastError = null;
        this.nextAttemptAt = null;
    }

    void updateStatus(SupplierOrderStatus status, LocalDateTime nextAttemptAt) {
        this.status = status;
        this.lastError = null;
        this.nextAttemptAt = nextAttemptAt;
    }

    void recordError(String error, LocalDateTime nextAttemptAt) {
        this.lastError = error;
        this.nextAttemptAt = nextAttemptAt;
    }

    void markFailed(String error) {
        this.status = SupplierOrderStatus.FAILED;
        this.lastError = error;
        this.nextAttemptAt = null;
    }

    SupplierOrderResult toResult() {
        return new SupplierOrderResult(id, productId, buyerRef, poNumber, cases, units, status, lastError);
    }
}