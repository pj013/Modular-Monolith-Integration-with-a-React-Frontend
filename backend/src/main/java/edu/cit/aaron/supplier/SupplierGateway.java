package edu.cit.aaron.supplier;

import java.util.List;

/** Public boundary for requesting replenishment in the supplier module. */
public interface SupplierGateway {

    SupplierOrderResult reorder(String productId, int unitsNeeded);

    List<SupplierOrderResult> listOrders();
}