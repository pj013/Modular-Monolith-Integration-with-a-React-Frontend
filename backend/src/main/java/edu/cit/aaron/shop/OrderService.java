package edu.cit.aaron.shop;

import edu.cit.aaron.inventory.InventoryItemDto;
import edu.cit.aaron.inventory.InventoryService;
import edu.cit.aaron.inventory.ProductNotFoundException;
import edu.cit.aaron.inventory.ReservationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Order module's core logic. This is the in-process integration point with the
 * Inventory module: Spring injects whatever bean implements InventoryService
 * (currently the package-private InventoryServiceImpl living in edu.cit.aaron.inventory),
 * and this class never references that implementation class or its JPA entities/repository.
 */
@Service
public class OrderService {

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        ReservationResult result = inventoryService.reserve(request.productId(), request.quantity());

        String status = result.success() ? "CONFIRMED" : "REJECTED";

        OrderEntity order = new OrderEntity(
                request.productId(),
                request.quantity(),
                status,
                result.reason(),
                LocalDateTime.now()
        );
        orderRepository.save(order);

        InventoryItemDto inventorySnapshot = resolveInventorySnapshot(request.productId(), result);

        return new OrderResponse(status, result.reason(), inventorySnapshot);
    }

    private InventoryItemDto resolveInventorySnapshot(String productId, ReservationResult result) {
        if (result.item() != null) {
            return result.item();
        }
        // result.item() is only null when the product didn't exist at all;
        // guard the lookup so a bad productId can't blow up the whole request.
        try {
            return inventoryService.getItem(productId);
        } catch (ProductNotFoundException ex) {
            return null;
        }
    }
}
