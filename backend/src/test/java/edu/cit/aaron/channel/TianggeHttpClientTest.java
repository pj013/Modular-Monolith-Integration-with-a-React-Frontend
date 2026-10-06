package edu.cit.aaron.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TianggeHttpClientTest {

    private HttpServer server;
    private final List<CapturedRequest> requests = new ArrayList<>();
    private TianggeHttpClient client;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tiangge/v1", exchange -> {
            byte[] requestBody = exchange.getRequestBody().readAllBytes();
            requests.add(new CapturedRequest(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders().getFirst("X-Client-Id"),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("X-Client-Instance"),
                    new String(requestBody, StandardCharsets.UTF_8)));
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        client = new TianggeHttpClient(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/tiangge/v1",
                "18-0668-202",
                "test-key",
                new ChannelInstanceId(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                new ObjectMapper());
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void sendsIdentityAndExpectedJsonForHeartbeatAndStock() {
        client.heartbeat("shop", "2026-10-06T00:00:00Z", 0);
        client.publishStock(List.of(new StockLevel("P100", 12)));

        assertEquals(2, requests.size());
        for (CapturedRequest request : requests) {
            assertEquals("18-0668-202", request.clientId());
            assertEquals("Bearer test-key", request.authorization());
            assertEquals("00000000-0000-0000-0000-000000000001", request.instanceId());
        }
        assertEquals("POST", requests.get(0).method());
        assertEquals("/tiangge/v1/instances/heartbeat", requests.get(0).path());
        assertTrue(requests.get(0).body().contains("\"appName\":\"shop\""));
        assertEquals("PUT", requests.get(1).method());
        assertEquals("/tiangge/v1/stock", requests.get(1).path());
        assertTrue(requests.get(1).body().contains("\"sellerSku\":\"P100\""));
        assertTrue(requests.get(1).body().contains("\"available\":12"));
    }

    private record CapturedRequest(
            String method, String path, String clientId, String authorization,
            String instanceId, String body) {
    }
}
