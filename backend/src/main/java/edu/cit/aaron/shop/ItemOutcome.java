package edu.cit.aaron.shop;

/**
 * outcome is "RESERVED" when the order was CONFIRMED and this item was actually
 * reserved; otherwise it's "OK" (this item alone would have succeeded) or the
 * specific validation failure (e.g. "Insufficient stock: ...", "Product not found: ...")
 * that this item hit - even for items that were individually fine, when some
 * OTHER item in the same order failed validation and the whole order was rejected.
 */
public record ItemOutcome(String productId, int quantity, String outcome) {
}
