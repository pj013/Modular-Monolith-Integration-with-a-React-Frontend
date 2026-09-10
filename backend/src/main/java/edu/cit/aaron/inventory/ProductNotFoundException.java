package edu.cit.aaron.inventory;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String productId) {
        super("Product not found: " + productId);
    }
}
