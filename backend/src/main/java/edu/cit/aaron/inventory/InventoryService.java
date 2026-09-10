package edu.cit.aaron.inventory;

/**
 * Public contract for the Inventory module. This is the ONLY inventory type the
 * Order module (or any other caller) is allowed to depend on - the implementation
 * class and the JPA entity/repository behind it are package-private and invisible
 * outside this package.
 */
public interface InventoryService {

    /**
     * @throws ProductNotFoundException if no item exists with the given productId
     */
    InventoryItemDto getItem(String productId);

    /**
     * Attempts to reserve (deduct) the given quantity from stock.
     * Rejects the reservation - without throwing - if the product doesn't exist,
     * the quantity is not positive, or the requested quantity exceeds current stock.
     */
    ReservationResult reserve(String productId, int quantity);
}
