package edu.cit.aaron.shop;

import edu.cit.aaron.inventory.InventoryItemDto;

import java.util.List;

public record OrderResponse(
        Long orderId,
        String status,
        String reason,
        List<ItemOutcome> items,
        List<InventoryItemDto> inventory
) {
}
