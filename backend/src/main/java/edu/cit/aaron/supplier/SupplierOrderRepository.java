package edu.cit.aaron.supplier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

interface SupplierOrderRepository extends JpaRepository<SupplierOrderEntity, Long> {

        List<SupplierOrderEntity> findAllByOrderByCreatedAtDesc();

    List<SupplierOrderEntity> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(
            SupplierOrderStatus status, LocalDateTime now);

    List<SupplierOrderEntity> findByStatusInAndPoNumberIsNotNullAndNextAttemptAtLessThanEqualOrderByUpdatedAt(
            Collection<SupplierOrderStatus> statuses, LocalDateTime now);
}