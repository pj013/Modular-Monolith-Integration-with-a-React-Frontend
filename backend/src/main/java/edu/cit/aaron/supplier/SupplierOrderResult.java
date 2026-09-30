package edu.cit.aaron.supplier;

public record SupplierOrderResult(
        Long id,
        String productId,
        String buyerRef,
        String poNumber,
        int cases,
        int units,
        SupplierOrderStatus status,
        String lastError) {
}