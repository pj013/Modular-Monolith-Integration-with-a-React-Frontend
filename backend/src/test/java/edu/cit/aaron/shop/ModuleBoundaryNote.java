package edu.cit.aaron.shop;

/**
 * This isn't an executable test - it documents the compile-time boundary.
 *
 * The Order module (this package) can only ever depend on:
 *   edu.cit.aaron.inventory.InventoryService   (public interface)
 *   edu.cit.aaron.inventory.InventoryItemDto   (public record)
 *   edu.cit.aaron.inventory.ReservationResult  (public record)
 *   edu.cit.aaron.inventory.ProductNotFoundException (public exception)
 *
 * If you uncomment the two lines below, the module will fail to compile,
 * proving InventoryServiceImpl and InventoryEntity are not accessible from
 * outside the edu.cit.aaron.inventory package:
 *
 *   import edu.cit.aaron.inventory.InventoryServiceImpl; // does not compile - package-private
 *   import edu.cit.aaron.inventory.InventoryEntity;      // does not compile - package-private
 */
final class ModuleBoundaryNote {
    private ModuleBoundaryNote() {
    }
}
