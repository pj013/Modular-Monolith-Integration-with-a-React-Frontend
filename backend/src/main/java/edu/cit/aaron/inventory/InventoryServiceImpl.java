package edu.cit.aaron.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Package-private on purpose: this class is an implementation detail of the
 * Inventory module. Other modules (e.g. Order) can only see it through the
 * public InventoryService interface, injected by Spring.
 */
@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository repository;

    InventoryServiceImpl(InventoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public InventoryItemDto getItem(String productId) {
        InventoryEntity entity = repository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return toDto(entity);
    }

    @Override
    @Transactional
    public ReservationResult reserve(String productId, int quantity) {
        Optional<InventoryEntity> found = repository.findById(productId);
        if (found.isEmpty()) {
            return new ReservationResult(false, "Product not found: " + productId, null);
        }

        InventoryEntity entity = found.get();

        if (quantity <= 0) {
            return new ReservationResult(false, "Quantity must be greater than zero", toDto(entity));
        }

        if (quantity > entity.getStock()) {
            return new ReservationResult(false,
                    "Insufficient stock: requested " + quantity + " but only " + entity.getStock() + " available",
                    toDto(entity));
        }

        entity.setStock(entity.getStock() - quantity);
        repository.save(entity);
        return new ReservationResult(true, null, toDto(entity));
    }

    private InventoryItemDto toDto(InventoryEntity entity) {
        return new InventoryItemDto(entity.getProductId(), entity.getName(), entity.getStock());
    }
}
