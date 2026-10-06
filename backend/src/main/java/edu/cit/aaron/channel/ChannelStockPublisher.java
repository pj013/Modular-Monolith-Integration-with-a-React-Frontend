package edu.cit.aaron.channel;

import edu.cit.aaron.inventory.events.InventoryStockChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.context.event.EventListener;

import java.util.List;

@Component
class ChannelStockPublisher implements ChannelGateway {

    private static final Logger log = LoggerFactory.getLogger(ChannelStockPublisher.class);
    private final TianggeHttpClient client;
    private final JdbcTemplate jdbcTemplate;
    private final ChannelStartup startup;

    ChannelStockPublisher(TianggeHttpClient client, JdbcTemplate jdbcTemplate, ChannelStartup startup) {
        this.client = client;
        this.jdbcTemplate = jdbcTemplate;
        this.startup = startup;
    }

    @Override
    public void publishStock(StockLevel stockLevel) {
        client.publishStock(java.util.List.of(stockLevel));
    }

    @EventListener
    void onStockChanged(InventoryStockChangedEvent event) {
        jdbcTemplate.update("""
                insert into channel_stock_outbox (seller_sku, available, ready, updated_at)
                values (?, ?, ?, now())
                on conflict (seller_sku) do update
                set available = excluded.available,
                    ready = channel_stock_outbox.ready and excluded.ready,
                    updated_at = now()
                """, event.productId(), event.available(), !MarketplaceOrderContext.decisionPending());
    }

    @Scheduled(fixedDelayString = "${app.channel.stock-dispatch-delay-ms:2000}", initialDelay = 2000)
    void dispatchQueuedStock() {
        if (!startup.initialized()) {
            return;
        }
        List<StockLevel> pending = jdbcTemplate.query(
                "select seller_sku, available from channel_stock_outbox where ready = true order by updated_at",
                (row, rowNumber) -> new StockLevel(row.getString(1), row.getInt(2)));
        for (StockLevel level : pending) {
            try {
                publishStock(level);
                jdbcTemplate.update(
                        "delete from channel_stock_outbox where seller_sku = ? and available = ? and ready = true",
                        level.sellerSku(), level.available());
            } catch (TianggeException ex) {
                log.error("Could not publish changed stock for {}; it remains queued",
                        level.sellerSku(), ex);
            }
        }
    }

    void publishAfterDecision(List<StockLevel> levels) {
        for (StockLevel level : levels) {
            jdbcTemplate.update(
                    "update channel_stock_outbox set ready = true where seller_sku = ?",
                    level.sellerSku());
            publishStock(level);
            jdbcTemplate.update(
                    "delete from channel_stock_outbox where seller_sku = ? and available = ? and ready = true",
                    level.sellerSku(), level.available());
        }
    }
}
