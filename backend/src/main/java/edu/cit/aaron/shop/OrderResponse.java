package edu.cit.aaron.shop;

import edu.cit.aaron.inventory.InventoryItemDto;

public record OrderResponse(String status, String reason, InventoryItemDto inventory) {
}
