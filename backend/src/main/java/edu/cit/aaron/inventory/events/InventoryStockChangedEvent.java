package edu.cit.aaron.inventory.events;

public record InventoryStockChangedEvent(String productId, int available) {
}
