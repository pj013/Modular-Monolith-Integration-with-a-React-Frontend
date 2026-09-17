package edu.cit.aaron.shop;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    // left join fetch avoids N+1 queries and the lazy-loading-after-session-closed
    // problem when the controller serializes each order's items to JSON.
    @Query("select distinct o from OrderEntity o left join fetch o.items order by o.createdAt desc")
    List<OrderEntity> findAllWithItemsOrderByCreatedAtDesc();
}
