package edu.cit.aaron.supplier;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
class SupplierGatewayAdapter implements SupplierGateway {

    private static final List<SupplierOrderStatus> OPEN_STATUSES = List.of(
            SupplierOrderStatus.ACCEPTED,
            SupplierOrderStatus.PICKING,
            SupplierOrderStatus.SHIPPED,
            SupplierOrderStatus.UNKNOWN);

    private final SupplierOrderRepository repository;
    private final LegacySupplyClient client;
    private final ApplicationEventPublisher eventPublisher;

    @SuppressWarnings("unused")
    SupplierGatewayAdapter(
            SupplierOrderRepository repository,
            LegacySupplyClient client,
            ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.client = client;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public SupplierOrderResult reorder(String productId, int unitsNeeded) {
        if (productId == null || productId.isBlank() || unitsNeeded < 1) {
            throw new IllegalArgumentException("Product ID and positive units are required");
        }
        SupplierOrderEntity order = repository.saveAndFlush(new SupplierOrderEntity(productId, unitsNeeded));
        order.assignReferences();
        repository.save(order);
        return order.toResult();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplierOrderResult> listOrders() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(SupplierOrderEntity::toResult)
                .toList();
    }

    @Scheduled(
            fixedDelayString = "${app.supplier.dispatch-delay-ms:30000}",
            initialDelayString = "${app.supplier.initial-delay-ms:5000}")
    @Transactional
    public void dispatchPendingOrders() {
        LocalDateTime now = LocalDateTime.now();
        for (SupplierOrderEntity order : repository.findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(
                SupplierOrderStatus.PENDING, now)) {
            try {
                SupplierAcknowledgement acknowledgement = client.placeOrder(
                        order.getProductId(), order.getUnits(), order.getBuyerRef(), order.getRequestId());
                order.recordSubmission(acknowledgement.poNumber(), acknowledgement.cases(),
                        acknowledgement.units(), acknowledgement.status());
                if (acknowledgement.status() == SupplierOrderStatus.DELIVERED) {
                    eventPublisher.publishEvent(new SupplierOrderDeliveredEvent(
                            order.getProductId(), acknowledgement.units(), order.getBuyerRef()));
                } else {
                    order.updateStatus(acknowledgement.status(), LocalDateTime.now().plusMinutes(1));
                }
                repository.save(order);
            } catch (LegacySupplyException ex) {
                if (ex.retryable()) {
                    long delaySeconds = Math.min(300, 5L << Math.min(order.getAttemptCount(), 6));
                    order.prepareRetry(LocalDateTime.now().plusSeconds(delaySeconds), safeMessage(ex));
                } else {
                    order.markFailed(safeMessage(ex));
                }
                repository.save(order);
            }
        }
    }

    @Scheduled(
            fixedDelayString = "${app.supplier.tracking-delay-ms:60000}",
            initialDelayString = "${app.supplier.tracking-delay-ms:60000}")
    @Transactional
    public void trackOpenOrders() {
        LocalDateTime now = LocalDateTime.now();
        for (SupplierOrderEntity order : repository
                .findByStatusInAndPoNumberIsNotNullAndNextAttemptAtLessThanEqualOrderByUpdatedAt(OPEN_STATUSES, now)) {
            SupplierOrderStatus previousStatus = order.getStatus();
            try {
                SupplierOrderStatus nextStatus = client.getStatus(order.getPoNumber());
                LocalDateTime nextCheck = nextStatus == SupplierOrderStatus.DELIVERED
                        ? null
                        : LocalDateTime.now().plusMinutes(1);
                order.updateStatus(nextStatus, nextCheck);
                repository.save(order);
                if (nextStatus == SupplierOrderStatus.DELIVERED
                        && previousStatus != SupplierOrderStatus.DELIVERED) {
                    eventPublisher.publishEvent(new SupplierOrderDeliveredEvent(
                            order.getProductId(), order.getReceivedUnits(), order.getBuyerRef()));
                }
            } catch (LegacySupplyException ex) {
                order.recordError(safeMessage(ex), LocalDateTime.now().plusMinutes(1));
                repository.save(order);
            }
        }
    }

    private static String safeMessage(LegacySupplyException ex) {
        String message = ex.getMessage();
        return message == null ? "Supplier request failed" : message.substring(0, Math.min(message.length(), 500));
    }
}