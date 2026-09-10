package edu.cit.aaron.shop;

import edu.cit.aaron.inventory.InventoryItemDto;
import edu.cit.aaron.inventory.InventoryService;
import edu.cit.aaron.inventory.ReservationResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OrderServiceTest {

    // A hand-written fake is enough to prove OrderService depends only on the
    // InventoryService interface - it never needs to know about InventoryServiceImpl.
    static class FakeInventoryService implements InventoryService {
        int stock;

        FakeInventoryService(int stock) {
            this.stock = stock;
        }

        @Override
        public InventoryItemDto getItem(String productId) {
            return new InventoryItemDto(productId, "Fake Item", stock);
        }

        @Override
        public ReservationResult reserve(String productId, int quantity) {
            if (quantity > stock) {
                return new ReservationResult(false, "Insufficient stock", new InventoryItemDto(productId, "Fake Item", stock));
            }
            stock -= quantity;
            return new ReservationResult(true, null, new InventoryItemDto(productId, "Fake Item", stock));
        }
    }

    @Test
    void confirmsOrderWhenStockIsSufficient() {
        FakeInventoryService inventory = new FakeInventoryService(10);
        OrderRepository repo = Mockito.mock(OrderRepository.class);
        OrderService orderService = new OrderService(inventory, repo);

        OrderResponse response = orderService.placeOrder(new OrderRequest("P100", 3));

        assertEquals("CONFIRMED", response.status());
        assertNull(response.reason());
        assertEquals(7, response.inventory().stock());
        Mockito.verify(repo).save(Mockito.any());
    }

    @Test
    void rejectsOrderWhenStockIsInsufficient() {
        FakeInventoryService inventory = new FakeInventoryService(0);
        OrderRepository repo = Mockito.mock(OrderRepository.class);
        OrderService orderService = new OrderService(inventory, repo);

        OrderResponse response = orderService.placeOrder(new OrderRequest("P300", 1));

        assertEquals("REJECTED", response.status());
        assertEquals("Insufficient stock", response.reason());
        assertEquals(0, response.inventory().stock());
        Mockito.verify(repo).save(Mockito.any());
    }
}
