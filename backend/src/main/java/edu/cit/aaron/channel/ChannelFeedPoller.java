package edu.cit.aaron.channel;

import com.fasterxml.jackson.databind.JsonNode;
import edu.cit.aaron.inventory.InventoryItemDto;
import edu.cit.aaron.inventory.InventoryService;
import edu.cit.aaron.inventory.ProductNotFoundException;
import edu.cit.aaron.shop.CancelOrderResponse;
import edu.cit.aaron.shop.MultiItemOrderRequest;
import edu.cit.aaron.shop.OrderItemRequest;
import edu.cit.aaron.shop.OrderResponse;
import edu.cit.aaron.shop.OrderService;
import edu.cit.aaron.supplier.SupplierGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
class ChannelFeedPoller {

    private static final Logger log = LoggerFactory.getLogger(ChannelFeedPoller.class);
    private static final String ORDER_REFERENCE_PREFIX = "marketplace:";

    private final ChannelStartup startup;
    private final TianggeHttpClient client;
    private final ChannelRepository repository;
    private final ChannelStockPublisher stockPublisher;
    private final InventoryService inventoryService;
    private final OrderService orderService;
    private final SupplierGateway supplierGateway;

    ChannelFeedPoller(
            ChannelStartup startup,
            TianggeHttpClient client,
            ChannelRepository repository,
            ChannelStockPublisher stockPublisher,
            InventoryService inventoryService,
            OrderService orderService,
            SupplierGateway supplierGateway) {
        this.startup = startup;
        this.client = client;
        this.repository = repository;
        this.stockPublisher = stockPublisher;
        this.inventoryService = inventoryService;
        this.orderService = orderService;
        this.supplierGateway = supplierGateway;
    }

    @Scheduled(fixedDelayString = "${app.channel.feed-delay-ms:3000}", initialDelay = 5000)
    void poll() {
        if (!startup.initialized()) {
            return;
        }
        long cursor = repository.cursor();
        JsonNode response = client.feed(cursor, 50);
        JsonNode events = response.path("events");
        if (!events.isArray()) {
            throw new IllegalStateException("Tiangge feed response did not contain an events array");
        }
        for (JsonNode event : events) {
            String eventId = requiredText(event, "eventId");
            long sequence = requiredLong(event, "seq");
            if (repository.isProcessed(eventId)) {
                repository.advanceCursor(sequence);
                continue;
            }
            try {
                Long localOrderId = process(event);
                repository.markProcessed(eventId, sequence, requiredText(event, "type"),
                        optionalText(event, "orderId"), localOrderId);
            } catch (TianggeException ex) {
                log.error("Marketplace event {} could not be completed; feed cursor remains at {}",
                        eventId, repository.cursor(), ex);
                return;
            }
        }
        if (response.hasNonNull("nextCursor")) {
            repository.advanceCursor(response.path("nextCursor").asLong());
        }
    }

    private Long process(JsonNode event) {
        String type = requiredText(event, "type");
        return switch (type) {
            case "ORDER_PLACED" -> processOrder(event);
            case "ORDER_CANCELLED" -> processCancellation(event);
            default -> {
                log.warn("Skipping unsupported marketplace feed event type {}", type);
                yield null;
            }
        };
    }

    private Long processOrder(JsonNode event) {
        String marketplaceOrderId = requiredText(event, "orderId");
        java.util.Map<String, Integer> quantities = new java.util.LinkedHashMap<>();
        JsonNode lines = event.path("lines");
        if (!lines.isArray() || lines.isEmpty()) {
            throw new IllegalArgumentException("Marketplace order has no lines");
        }
        for (JsonNode line : lines) {
            String sellerSku = requiredText(line, "sellerSku");
            int quantity = Math.toIntExact(requiredLong(line, "qty"));
            quantities.merge(sellerSku, quantity, Math::addExact);
        }
        List<OrderItemRequest> items = quantities.entrySet().stream()
                .map(entry -> new OrderItemRequest(entry.getKey(), entry.getValue()))
                .toList();

        String reference = ORDER_REFERENCE_PREFIX + marketplaceOrderId;
        ChannelOrderLink existingLink = repository.orderLink(marketplaceOrderId).orElse(null);
        if (existingLink != null) {
            if ("BACKORDERED".equals(existingLink.initialDecision())) {
                repository.saveBackorder(marketplaceOrderId, reference, existingLink.localOrderId(), items);
            }
            client.decide(marketplaceOrderId, existingLink.initialDecision(),
                    existingLink.localOrderId().toString(), null);
            if ("ACCEPTED".equals(existingLink.initialDecision())) {
                stockPublisher.publishAfterDecision(currentStockLevels(items));
            }
            return existingLink.localOrderId();
        }

        ensureSupplierOrdersForShortages(items);
        MarketplaceOrderContext.begin();
        OrderResponse localOrder;
        try {
            localOrder = orderService.placeOrderOrBackorder(new MultiItemOrderRequest(items), reference);
        } finally {
            MarketplaceOrderContext.end();
        }

        String decision = switch (localOrder.status()) {
            case "CONFIRMED" -> "ACCEPTED";
            case "BACKORDERED" -> "BACKORDERED";
            default -> "REJECTED";
        };
        if ("BACKORDERED".equals(decision)) {
            ensureSupplierOrdersForShortages(items);
            repository.saveBackorder(marketplaceOrderId, reference, localOrder.orderId(), items);
        }
        repository.saveOrderLink(marketplaceOrderId, reference, localOrder.orderId(), decision);
        String reason = truncate(localOrder.reason(), 200);
        client.decide(marketplaceOrderId, decision, localOrder.orderId().toString(), reason);
        if ("ACCEPTED".equals(decision)) {
            List<StockLevel> levels = localOrder.inventory().stream()
                    .map(item -> new StockLevel(item.productId(), item.stock()))
                    .toList();
            stockPublisher.publishAfterDecision(levels);
        }
        return localOrder.orderId();
    }

    private void ensureSupplierOrdersForShortages(List<OrderItemRequest> items) {
        for (OrderItemRequest item : items) {
            try {
                InventoryItemDto current = inventoryService.getItem(item.productId());
                if (current.stock() < item.quantity()) {
                    supplierGateway.ensureReorder(item.productId(), item.quantity() - current.stock());
                }
            } catch (ProductNotFoundException ignored) {
                // Let OrderService persist the normal rejected outcome for an unknown product.
            }
        }
    }

    private List<StockLevel> currentStockLevels(List<OrderItemRequest> items) {
        return items.stream()
                .map(item -> inventoryService.getItem(item.productId()))
                .map(item -> new StockLevel(item.productId(), item.stock()))
                .toList();
    }

    private Long processCancellation(JsonNode event) {
        String marketplaceOrderId = requiredText(event, "orderId");
        String reference = ORDER_REFERENCE_PREFIX + marketplaceOrderId;
        MarketplaceOrderContext.begin();
        CancelOrderResponse cancellation;
        try {
            cancellation = orderService.cancelOrderForExternalReference(reference);
        } finally {
            MarketplaceOrderContext.end();
        }
        client.confirmCancellation(marketplaceOrderId);
        repository.markBackorderCancelled(marketplaceOrderId);
        List<StockLevel> levels = cancellation.inventory().stream()
                .filter(item -> item != null)
                .map(item -> new StockLevel(item.productId(), item.stock()))
                .toList();
        if (!levels.isEmpty()) {
            stockPublisher.publishAfterDecision(levels);
        }
        return cancellation.orderId();
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Marketplace event is missing " + field);
        }
        return value.asText();
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isTextual() ? null : value.asText();
    }

    private static long requiredLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong() || value.asLong() < 1) {
            throw new IllegalArgumentException("Marketplace event has invalid " + field);
        }
        return value.asLong();
    }

    private static String truncate(String value, int maxLength) {
        return value == null ? null : value.substring(0, Math.min(value.length(), maxLength));
    }

}
