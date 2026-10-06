package edu.cit.aaron.shop;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(Long orderId) {
        super("Order not found: " + orderId);
    }

    public OrderNotFoundException(String reference) {
        super("Order not found for reference: " + reference);
    }
}
