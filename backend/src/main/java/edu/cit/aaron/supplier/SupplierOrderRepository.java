package edu.cit.aaron.supplier;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface SupplierOrderRepository extends JpaRepository<SupplierOrderEntity, Long> {

        List<SupplierOrderEntity> findAllByOrderByCreatedAtDesc();

    Optional<SupplierOrderEntity> findFirstByProductIdAndStatusInOrderByCreatedAtDesc(
            String productId, Collection<SupplierOrderStatus> statuses);

    List<SupplierOrderEntity> findByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAt(
            SupplierOrderStatus status, LocalDateTime now);

    List<SupplierOrderEntity> findByStatusInAndPoNumberIsNotNullAndNextAttemptAtLessThanEqualOrderByUpdatedAt(
            Collection<SupplierOrderStatus> statuses, LocalDateTime now);
}