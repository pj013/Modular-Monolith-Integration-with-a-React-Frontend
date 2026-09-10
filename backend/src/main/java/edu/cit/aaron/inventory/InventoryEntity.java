package edu.cit.aaron.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Internal persistence model. Not exposed outside this package -
 * external callers (including the Order module) only ever see InventoryItemDto.
 */
@Entity
@Table(name = "inventory")
class InventoryEntity {

    @Id
    @Column(name = "product_id")
    private String productId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "stock", nullable = false)
    private int stock;

    protected InventoryEntity() {
        // required by JPA
    }

    InventoryEntity(String productId, String name, int stock) {
        this.productId = productId;
        this.name = name;
        this.stock = stock;
    }

    String getProductId() {
        return productId;
    }

    String getName() {
        return name;
    }

    int getStock() {
        return stock;
    }

    void setStock(int stock) {
        this.stock = stock;
    }
}
