package edu.cit.aaron.shop;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody MultiItemOrderRequest request) {
        OrderResponse response = orderService.placeOrder(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public List<OrderSummaryDto> listOrders() {
        return orderService.listOrders();
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<CancelOrderResponse> cancelOrder(@PathVariable Long orderId) {
        CancelOrderResponse response = orderService.cancelOrder(orderId);
        return ResponseEntity.ok(response);
    }
}
