package edu.cit.aaron.shop;

import java.time.LocalDateTime;
import java.util.List;

public record OrderSummaryDto(
        Long orderId,
        String status,
        String reason,
        List<OrderItemDto> items,
        LocalDateTime createdAt
) {
}
