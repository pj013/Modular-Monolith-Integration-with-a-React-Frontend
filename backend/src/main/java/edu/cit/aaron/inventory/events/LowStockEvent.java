package edu.cit.aaron.inventory.events;

/**
 * Published by the Inventory module whenever a reserve() drops a product's
 * stock below the configured low-stock threshold. Lives in its own "events"
 * sub-package (not the "notification" package) so Notification can depend on
 * this event class without Inventory ever knowing Notification exists.
 */
public record LowStockEvent(String productId, String productName, int remainingStock, int threshold) {
}
