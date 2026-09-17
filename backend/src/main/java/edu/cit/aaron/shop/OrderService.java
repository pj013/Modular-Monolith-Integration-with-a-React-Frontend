package edu.cit.aaron.shop;

import edu.cit.aaron.inventory.InventoryItemDto;
import edu.cit.aaron.inventory.InventoryService;
import edu.cit.aaron.inventory.ProductNotFoundException;
import edu.cit.aaron.inventory.ReservationResult;
import edu.cit.aaron.shop.events.OrderPlacedEvent;
import edu.cit.aaron.shop.events.OrderRejectedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Order module's core logic. This is the in-process integration point with the
 * Inventory module: Spring injects whatever bean implements InventoryService
 * (currently the package-private InventoryServiceImpl living in edu.cit.aaron.inventory),
 * and this class never references that implementation class or its JPA entities/repository.
 *
 * Cross-module notification is one-way and decoupled: this class publishes
 * OrderPlacedEvent / OrderRejectedEvent via ApplicationEventPublisher and has no
 * idea the Notification module exists.
 */
@Service
public class OrderService {

    static final String STATUS_CONFIRMED = "CONFIRMED";
    static final String STATUS_REJECTED = "REJECTED";
    static final String STATUS_CANCELLED = "CANCELLED";
    private static final String OUTCOME_OK = "OK";
    private static final String OUTCOME_RESERVED = "RESERVED";

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository,
                        ApplicationEventPublisher eventPublisher) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Validates every line item against current stock BEFORE reserving anything.
     * If any single item fails validation, the whole order is REJECTED and
     * InventoryService.reserve() is never called for any item - true all-or-nothing.
     */
    @Transactional
    public OrderResponse placeOrder(MultiItemOrderRequest request) {
        Map<String, String> validation = validateItems(request.items());
        boolean allValid = validation.values().stream().allMatch(OUTCOME_OK::equals);

        OrderEntity order;
        List<ItemOutcome> outcomes = new ArrayList<>();
        List<InventoryItemDto> inventorySnapshot = new ArrayList<>();

        if (!allValid) {
            String reason = validation.entrySet().stream()
                    .filter(e -> !OUTCOME_OK.equals(e.getValue()))
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .findFirst()
                    .orElse("One or more items could not be fulfilled");

            order = new OrderEntity(STATUS_REJECTED, reason, LocalDateTime.now());
            for (OrderItemRequest item : request.items()) {
                order.addItem(new OrderItemEntity(item.productId(), item.quantity()));
                outcomes.add(new ItemOutcome(item.productId(), item.quantity(), validation.get(item.productId())));
                inventorySnapshot.add(safeGetItem(item.productId()));
            }
            orderRepository.save(order);

            eventPublisher.publishEvent(new OrderRejectedEvent(order.getOrderId(), reason));
            return new OrderResponse(order.getOrderId(), STATUS_REJECTED, reason, outcomes, inventorySnapshot);
        }

        // All items validated - now, and only now, actually reserve each one.
        order = new OrderEntity(STATUS_CONFIRMED, null, LocalDateTime.now());
        for (OrderItemRequest item : request.items()) {
            ReservationResult result = inventoryService.reserve(item.productId(), item.quantity());
            if (!result.success()) {
                // Extremely unlikely given the pre-validation above (would require a
                // concurrent order racing this one between validate and reserve).
                // Throwing rolls back the whole @Transactional method, including any
                // items already reserved in this loop - preserving all-or-nothing.
                throw new IllegalStateException(
                        "Stock changed concurrently while placing the order: " + result.reason());
            }
            order.addItem(new OrderItemEntity(item.productId(), item.quantity()));
            outcomes.add(new ItemOutcome(item.productId(), item.quantity(), OUTCOME_RESERVED));
            inventorySnapshot.add(result.item());
        }
        orderRepository.save(order);

        eventPublisher.publishEvent(new OrderPlacedEvent(order.getOrderId()));
        return new OrderResponse(order.getOrderId(), STATUS_CONFIRMED, null, outcomes, inventorySnapshot);
    }

    @Transactional
    public CancelOrderResponse cancelOrder(Long orderId) {
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (STATUS_CANCELLED.equals(order.getStatus())) {
            throw new OrderConflictException("Order " + orderId + " is already cancelled");
        }
        if (STATUS_REJECTED.equals(order.getStatus())) {
            throw new OrderConflictException("Order " + orderId + " was rejected - nothing was reserved, so there is nothing to restock");
        }

        List<InventoryItemDto> inventorySnapshot = new ArrayList<>();
        for (OrderItemEntity item : order.getItems()) {
            inventorySnapshot.add(inventoryService.restock(item.getProductId(), item.getQuantity()));
        }

        order.setStatus(STATUS_CANCELLED);
        orderRepository.save(order);

        return new CancelOrderResponse(orderId, STATUS_CANCELLED, inventorySnapshot);
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryDto> listOrders() {
        return orderRepository.findAllWithItemsOrderByCreatedAtDesc().stream()
                .map(o -> new OrderSummaryDto(
                        o.getOrderId(),
                        o.getStatus(),
                        o.getReason(),
                        o.getItems().stream().map(i -> new OrderItemDto(i.getProductId(), i.getQuantity())).toList(),
                        o.getCreatedAt()))
                .toList();
    }

    /**
     * Validates each requested item against CURRENT stock without reserving anything.
     * Returns a map of productId -> "OK" or a human-readable rejection reason.
     * Uses a LinkedHashMap to preserve request order for deterministic "first failure" reasons.
     */
    private Map<String, String> validateItems(List<OrderItemRequest> items) {
        Map<String, String> results = new LinkedHashMap<>();
        for (OrderItemRequest item : items) {
            InventoryItemDto current;
            try {
                current = inventoryService.getItem(item.productId());
            } catch (ProductNotFoundException ex) {
                results.put(item.productId(), "Product not found: " + item.productId());
                continue;
            }

            if (item.quantity() == null || item.quantity() <= 0) {
                results.put(item.productId(), "Quantity must be greater than zero");
            } else if (item.quantity() > current.stock()) {
                results.put(item.productId(),
                        "Insufficient stock: requested " + item.quantity() + " but only " + current.stock() + " available");
            } else {
                results.put(item.productId(), OUTCOME_OK);
            }
        }
        return results;
    }

    private InventoryItemDto safeGetItem(String productId) {
        try {
            return inventoryService.getItem(productId);
        } catch (ProductNotFoundException ex) {
            return null;
        }
    }
}
