package edu.cit.aaron.shop.events;

/**
 * Published by OrderService when an order is CONFIRMED. Lives in its own
 * "events" sub-package (not the "shop" package itself and not "notification")
 * so Notification can depend on this event class without ever importing
 * OrderService or anything else from the shop module.
 */
public record OrderPlacedEvent(Long orderId) {
}
