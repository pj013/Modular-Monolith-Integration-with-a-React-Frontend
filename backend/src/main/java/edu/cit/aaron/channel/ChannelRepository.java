package edu.cit.aaron.channel;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
class ChannelRepository {

    private final JdbcTemplate jdbcTemplate;

    ChannelRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    long cursor() {
        Long cursor = jdbcTemplate.queryForObject(
                "select feed_cursor from channel_state where state_id = 1", Long.class);
        if (cursor == null) {
            throw new IllegalStateException("Marketplace feed cursor row is missing");
        }
        return cursor;
    }

    boolean isProcessed(String eventId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "select exists (select 1 from channel_events where event_id = ?)",
                Boolean.class, eventId);
        return Boolean.TRUE.equals(exists);
    }

    void markProcessed(String eventId, long sequence, String type, String orderId, Long localOrderId) {
        jdbcTemplate.update("""
                insert into channel_events
                    (event_id, sequence_number, event_type, marketplace_order_id, local_order_id)
                values (?, ?, ?, ?, ?)
                on conflict (event_id) do nothing
                """, eventId, sequence, type, orderId, localOrderId);
        jdbcTemplate.update(
                "update channel_state set feed_cursor = greatest(feed_cursor, ?) where state_id = 1",
                sequence);
    }

    void advanceCursor(long cursor) {
        jdbcTemplate.update(
                "update channel_state set feed_cursor = greatest(feed_cursor, ?) where state_id = 1",
                cursor);
    }

    Optional<ChannelOrderLink> orderLink(String marketplaceOrderId) {
        List<ChannelOrderLink> links = jdbcTemplate.query("""
                select marketplace_order_id, external_reference, local_order_id, initial_decision
                from channel_order_links where marketplace_order_id = ?
                """, (row, rowNumber) -> new ChannelOrderLink(
                row.getString(1), row.getString(2), row.getLong(3), row.getString(4)), marketplaceOrderId);
        return links.stream().findFirst();
    }

    void saveOrderLink(String marketplaceOrderId, String externalReference, Long localOrderId, String decision) {
        jdbcTemplate.update("""
                insert into channel_order_links
                    (marketplace_order_id, external_reference, local_order_id, initial_decision)
                values (?, ?, ?, ?)
                on conflict (marketplace_order_id) do nothing
                """, marketplaceOrderId, externalReference, localOrderId, decision);
    }

    void saveBackorder(
            String marketplaceOrderId, String externalReference, Long localOrderId,
            List<edu.cit.aaron.shop.OrderItemRequest> items) {
        jdbcTemplate.update("""
                insert into channel_backorders
                    (marketplace_order_id, external_reference, local_order_id, status)
                values (?, ?, ?, 'WAITING')
                on conflict (marketplace_order_id) do nothing
                """, marketplaceOrderId, externalReference, localOrderId);
        for (var item : items) {
            jdbcTemplate.update("""
                    insert into channel_backorder_items (marketplace_order_id, product_id, quantity)
                    values (?, ?, ?)
                    on conflict (marketplace_order_id, product_id) do nothing
                    """, marketplaceOrderId, item.productId(), item.quantity());
        }
    }

    List<BackorderReference> waitingBackordersForProduct(String productId) {
        return jdbcTemplate.query("""
                select b.marketplace_order_id, b.external_reference, b.local_order_id
                from channel_backorders b
                join channel_backorder_items i using (marketplace_order_id)
                where b.status = 'WAITING' and i.product_id = ?
                order by b.created_at
                """, (row, rowNumber) -> new BackorderReference(
                row.getString(1), row.getString(2), row.getLong(3)), productId);
    }

    List<BackorderReference> waitingBackorders() {
        return jdbcTemplate.query("""
                select marketplace_order_id, external_reference, local_order_id
                from channel_backorders where status = 'WAITING'
                order by created_at
                """, (row, rowNumber) -> new BackorderReference(
                row.getString(1), row.getString(2), row.getLong(3)));
    }

    List<BackorderResolution> pendingBackorderResolutions() {
        return jdbcTemplate.query("""
                select marketplace_order_id, external_reference, local_order_id, resolution_status
                from channel_backorders where status = 'RESOLUTION_PENDING'
                order by updated_at
                """, (row, rowNumber) -> new BackorderResolution(
                row.getString(1), row.getString(2), row.getLong(3), row.getString(4)));
    }

    List<String> backorderProductIds(String marketplaceOrderId) {
        return jdbcTemplate.queryForList("""
                select product_id from channel_backorder_items
                where marketplace_order_id = ? order by product_id
                """, String.class, marketplaceOrderId);
    }

    int backorderQuantity(String marketplaceOrderId, String productId) {
        Integer quantity = jdbcTemplate.queryForObject("""
                select quantity from channel_backorder_items
                where marketplace_order_id = ? and product_id = ?
                """, Integer.class, marketplaceOrderId, productId);
        if (quantity == null) {
            throw new IllegalStateException("Marketplace backorder item is missing");
        }
        return quantity;
    }

    void queueBackorderResolution(String marketplaceOrderId, String status) {
        jdbcTemplate.update("""
                update channel_backorders
                set status = 'RESOLUTION_PENDING', resolution_status = ?, updated_at = now()
                where marketplace_order_id = ? and status in ('WAITING', 'RESOLUTION_PENDING')
                """, status, marketplaceOrderId);
    }

    void markBackorderResolved(String marketplaceOrderId) {
        jdbcTemplate.update("""
                update channel_backorders set status = 'RESOLVED', updated_at = now()
                where marketplace_order_id = ? and status = 'RESOLUTION_PENDING'
                """, marketplaceOrderId);
    }

    void markBackorderCancelled(String marketplaceOrderId) {
        jdbcTemplate.update("""
                update channel_backorders set status = 'CANCELLED', updated_at = now()
                where marketplace_order_id = ? and status = 'WAITING'
                """, marketplaceOrderId);
    }
}

record ChannelOrderLink(String marketplaceOrderId, String externalReference, Long localOrderId,
                        String initialDecision) {
}

record BackorderReference(String marketplaceOrderId, String externalReference, Long localOrderId) {
}

record BackorderResolution(String marketplaceOrderId, String externalReference, Long localOrderId,
                           String resolutionStatus) {
}
