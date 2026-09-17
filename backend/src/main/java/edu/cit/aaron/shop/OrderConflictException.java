package edu.cit.aaron.shop;

public class OrderConflictException extends RuntimeException {

    public OrderConflictException(String message) {
        super(message);
    }
}
