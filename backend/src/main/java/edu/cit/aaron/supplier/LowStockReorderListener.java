package edu.cit.aaron.supplier;

import edu.cit.aaron.inventory.events.LowStockEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
class LowStockReorderListener {

    private final SupplierGateway supplierGateway;

    @SuppressWarnings("unused")
    LowStockReorderListener(SupplierGateway supplierGateway) {
        this.supplierGateway = supplierGateway;
    }

    @EventListener
    public void onLowStock(LowStockEvent event) {
        int unitsNeeded = Math.max(1, event.threshold() - event.remainingStock());
        supplierGateway.reorder(event.productId(), unitsNeeded);
    }
}