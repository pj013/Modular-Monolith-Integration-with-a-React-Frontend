package edu.cit.aaron.inventory;

/**
 * Public read model for an inventory item. This is the only inventory shape
 * that ever crosses the module boundary (used by the Order module and by JSON responses).
 */
public record InventoryItemDto(String productId, String name, int stock) {
}
