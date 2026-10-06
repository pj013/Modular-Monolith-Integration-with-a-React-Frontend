package edu.cit.aaron.channel;

import edu.cit.aaron.inventory.InventoryService;
import edu.cit.aaron.shop.OrderItemRequest;
import edu.cit.aaron.shop.OrderResponse;
import edu.cit.aaron.shop.OrderService;
import edu.cit.aaron.supplier.SupplierGateway;
import edu.cit.aaron.supplier.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
class ChannelBackorderResolver {

    private static final Logger log = LoggerFactory.getLogger(ChannelBackorderResolver.class);
    private final ChannelRepository repository;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final TianggeHttpClient client;
    private final ChannelStockPublisher stockPublisher;
    private final ChannelStartup startup;

    ChannelBackorderResolver(
            ChannelRepository repository,
            OrderService orderService,
            InventoryService inventoryService,
            SupplierGateway supplierGateway,
            TianggeHttpClient client,
            ChannelStockPublisher stockPublisher,
            ChannelStartup startup) {
        this.repository = repository;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.client = client;
        this.stockPublisher = stockPublisher;
        this.startup = startup;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onSupplierDelivery(SupplierOrderDeliveredEvent event) {
        for (BackorderReference backorder : repository.waitingBackordersForProduct(event.productId())) {
            tryFulfill(backorder);
        }
        dispatchPendingResolutions();
    }

    @Scheduled(fixedDelayString = "${app.channel.feed-delay-ms:3000}", initialDelay = 8000)
    void recoverBackorders() {
        for (BackorderReference backorder : repository.waitingBackorders()) {
            tryFulfill(backorder);
        }
        dispatchPendingResolutions();
    }

    private void tryFulfill(BackorderReference backorder) {
        MarketplaceOrderContext.begin();
        OrderResponse response;
        try {
            response = orderService.fulfillBackorderedOrder(backorder.externalReference());
        } catch (IllegalStateException ex) {
            log.warn("Could not reserve stock for backordered marketplace order {} yet",
                    backorder.marketplaceOrderId(), ex);
            return;
        } finally {
            MarketplaceOrderContext.end();
        }
        if ("CONFIRMED".equals(response.status())) {
            repository.queueBackorderResolution(backorder.marketplaceOrderId(), "ACCEPTED");
        } else if ("BACKORDERED".equals(response.status())) {
            ensureRemainingSupplierOrders(backorder);
        }
    }

    private void ensureRemainingSupplierOrders(BackorderReference backorder) {
        for (String productId : repository.backorderProductIds(backorder.marketplaceOrderId())) {
            var item = inventoryService.getItem(productId);
            int required = repository.backorderQuantity(backorder.marketplaceOrderId(), productId);
            if (item.stock() < required) {
                supplierGateway.ensureReorder(productId, required - item.stock());
            }
        }
    }

    private void dispatchPendingResolutions() {
        if (!startup.initialized()) {
            return;
        }
        for (BackorderResolution resolution : repository.pendingBackorderResolutions()) {
            try {
                if ("CANCELLED".equals(resolution.resolutionStatus())) {
                    orderService.cancelOrderForExternalReference(resolution.externalReference());
                }
                client.resolveBackorder(resolution.marketplaceOrderId(), resolution.resolutionStatus());
                List<StockLevel> currentStock = repository.backorderProductIds(resolution.marketplaceOrderId())
                        .stream()
                        .map(inventoryService::getItem)
                        .map(item -> new StockLevel(item.productId(), item.stock()))
                        .toList();
                stockPublisher.publishAfterDecision(currentStock);
                repository.markBackorderResolved(resolution.marketplaceOrderId());
            } catch (TianggeException ex) {
                log.error("Could not resolve marketplace backorder {}; it will be retried",
                        resolution.marketplaceOrderId(), ex);
            }
        }
    }
}
