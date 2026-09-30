package edu.cit.aaron.supplier;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/supplier/orders")
@SuppressWarnings("unused")
class SupplierController {

    private final SupplierGateway supplierGateway;

    SupplierController(SupplierGateway supplierGateway) {
        this.supplierGateway = supplierGateway;
    }

    @GetMapping
    public List<SupplierOrderResult> listOrders() {
        return supplierGateway.listOrders();
    }
}