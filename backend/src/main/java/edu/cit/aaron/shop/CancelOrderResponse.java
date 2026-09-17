package edu.cit.aaron.shop;

import edu.cit.aaron.inventory.InventoryItemDto;

import java.util.List;

public record CancelOrderResponse(Long orderId, String status, List<InventoryItemDto> inventory) {
}
