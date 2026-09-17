package edu.cit.aaron.inventory;

import edu.cit.aaron.inventory.events.LowStockEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Package-private on purpose: this class is an implementation detail of the
 * Inventory module. Other modules (e.g. Order, Notification) can only see it
 * through the public InventoryService interface, injected by Spring.
 */
@Service
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository repository;
    private final ApplicationEventPublisher eventPublisher;
    private final int lowStockThreshold;

    InventoryServiceImpl(
            InventoryRepository repository,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.inventory.low-stock-threshold:5}") int lowStockThreshold) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.lowStockThreshold = lowStockThreshold;
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

        if (entity.getStock() < lowStockThreshold) {
            eventPublisher.publishEvent(
                    new LowStockEvent(entity.getProductId(), entity.getName(), entity.getStock(), lowStockThreshold));
        }

        return new ReservationResult(true, null, toDto(entity));
    }

    @Override
    @Transactional
    public InventoryItemDto restock(String productId, int quantity) {
        InventoryEntity entity = repository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        entity.setStock(entity.getStock() + quantity);
        repository.save(entity);
        return toDto(entity);
    }

    private InventoryItemDto toDto(InventoryEntity entity) {
        return new InventoryItemDto(entity.getProductId(), entity.getName(), entity.getStock());
    }
}
