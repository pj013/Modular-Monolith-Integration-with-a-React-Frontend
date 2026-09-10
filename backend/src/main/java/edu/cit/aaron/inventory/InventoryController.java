package edu.cit.aaron.inventory;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only HTTP surface for inventory data (used by the React frontend to build
 * the product dropdown). Not used by the Order module, which talks to InventoryService directly.
 */
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryRepository repository;
    private final InventoryService inventoryService;

    InventoryController(InventoryRepository repository, InventoryService inventoryService) {
        this.repository = repository;
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public List<InventoryItemDto> listInventory() {
        return repository.findAll().stream()
                .map(e -> new InventoryItemDto(e.getProductId(), e.getName(), e.getStock()))
                .toList();
    }

    @GetMapping("/{productId}")
    public InventoryItemDto getOne(@PathVariable String productId) {
        return inventoryService.getItem(productId);
    }
}
