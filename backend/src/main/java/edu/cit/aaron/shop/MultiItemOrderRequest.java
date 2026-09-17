package edu.cit.aaron.shop;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record MultiItemOrderRequest(
        @NotEmpty(message = "items must not be empty") @Valid List<OrderItemRequest> items
) {
}
