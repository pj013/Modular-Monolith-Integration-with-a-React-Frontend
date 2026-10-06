package edu.cit.aaron.channel;

import edu.cit.aaron.inventory.InventoryItemDto;
import edu.cit.aaron.inventory.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
class ChannelStartup {

    private static final Logger log = LoggerFactory.getLogger(ChannelStartup.class);
    private final ChannelInstanceId instanceId;
    private final TianggeHttpClient client;
    private final InventoryService inventoryService;
    private final Environment environment;
    private final String appName;
    private final String clientId;
    private volatile Instant startedAt;
    private volatile boolean initialized;

    ChannelStartup(
            ChannelInstanceId instanceId,
            TianggeHttpClient client,
            InventoryService inventoryService,
            Environment environment,
            @Value("${spring.application.name}") String appName,
            @Value("${app.channel.client-id}") String clientId) {
        this.instanceId = instanceId;
        this.client = client;
        this.inventoryService = inventoryService;
        this.environment = environment;
        this.appName = appName;
        this.clientId = clientId;
    }

    @EventListener(ApplicationReadyEvent.class)
    void start() {
        startedAt = Instant.now();
        log.info("Marketplace channel instance started: {}", instanceId.value());
        if (!client.configured()) {
            log.error("Marketplace channel is disabled because TIANGGE_CLIENT_ID or LS_API_KEY is missing");
            return;
        }
        heartbeatAndInitialize();
    }

    @Scheduled(fixedRate = 30_000, initialDelay = 30_000)
    void heartbeatAndInitialize() {
        if (startedAt == null || clientId.isBlank() || !client.configured()) {
            return;
        }
        try {
            client.heartbeat(appName, startedAt.toString(),
                    Math.max(0, Instant.now().getEpochSecond() - startedAt.getEpochSecond()));
            if (!initialized) {
                List<Map<String, String>> listings = listings();
                client.publishListings(listings);
                client.publishStock(listings.stream()
                        .map(listing -> currentStock(listing.get("sellerSku")))
                        .toList());
                initialized = true;
                log.info("Marketplace listings and initial stock published");
            }

        } catch (TianggeException ex) {
            log.error("Marketplace startup/heartbeat call failed; it will be retried", ex);
        }
    }

    boolean initialized() {
        return initialized;
    }

    private List<Map<String, String>> listings() {
        return List.of(
                listing("P100", "Wireless Mouse"),
                listing("P200", "Mechanical Keyboard"),
                listing("P300", "USB-C Hub"));
    }

    private Map<String, String> listing(String productId, String title) {
        String supplierSku = environment.getRequiredProperty("app.supplier.products." + productId + ".sku");
        return Map.of("sellerSku", productId, "title", title, "supplierSku", supplierSku);
    }

    private StockLevel currentStock(String productId) {
        InventoryItemDto item = inventoryService.getItem(productId);
        return new StockLevel(productId, item.stock());
    }
}
