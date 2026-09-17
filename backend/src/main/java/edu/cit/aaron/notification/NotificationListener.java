package edu.cit.aaron.notification;

import edu.cit.aaron.inventory.events.LowStockEvent;
import edu.cit.aaron.shop.events.OrderPlacedEvent;
import edu.cit.aaron.shop.events.OrderRejectedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Listens for domain events published by Order and Inventory. Notice the imports:
 * only the event record classes (edu.cit.aaron.shop.events.*, edu.cit.aaron.inventory.events.*).
 * This class never imports OrderService or InventoryService - it has no idea how an
 * order gets confirmed or how stock gets reserved, it only reacts to "it happened".
 *
 * Listeners run synchronously (no @Async) on purpose - see README for why.
 */
@Component
class NotificationListener {

    private final NotificationRepository repository;

    NotificationListener(NotificationRepository repository) {
        this.repository = repository;
    }

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        save("Order O" + event.orderId() + " confirmed");
    }

    @EventListener
    public void onOrderRejected(OrderRejectedEvent event) {
        save("Order O" + event.orderId() + " rejected: " + event.reason());
    }

    @EventListener
    public void onLowStock(LowStockEvent event) {
        save("Low stock alert: " + event.productName() + " (" + event.productId() + ") has "
                + event.remainingStock() + " left, below threshold of " + event.threshold() + " - reorder needed");
    }

    private void save(String message) {
        repository.save(new NotificationEntity(message, LocalDateTime.now()));
    }
}
