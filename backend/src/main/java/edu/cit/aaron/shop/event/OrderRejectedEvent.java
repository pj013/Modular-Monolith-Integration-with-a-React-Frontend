package edu.cit.aaron.shop.events;

/**
 * Published by OrderService when an order is REJECTED (any line item failed
 * validation, so nothing was reserved).
 */
public record OrderRejectedEvent(Long orderId, String reason) {
}
