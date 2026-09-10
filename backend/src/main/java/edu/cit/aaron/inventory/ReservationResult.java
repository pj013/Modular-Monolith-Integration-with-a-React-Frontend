package edu.cit.aaron.inventory;

/**
 * Outcome of a reserve() call.
 *
 * @param success whether the requested quantity was reserved (stock decremented)
 * @param reason  null when successful; a human-readable rejection reason otherwise
 * @param item    the inventory item's state after the attempt (null only if the product does not exist)
 */
public record ReservationResult(boolean success, String reason, InventoryItemDto item) {
}
