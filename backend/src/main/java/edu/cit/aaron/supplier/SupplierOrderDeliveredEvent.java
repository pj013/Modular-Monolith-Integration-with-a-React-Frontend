package edu.cit.aaron.supplier;

public record SupplierOrderDeliveredEvent(String productId, int units, String buyerRef) {
}