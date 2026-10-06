package edu.cit.aaron.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
class TianggeHttpClient {

    private static final int MAX_ATTEMPTS = 3;
    private final URI baseUri;
    private final String clientId;
    private final String apiKey;
    private final ChannelInstanceId instanceId;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    TianggeHttpClient(
            @Value("${app.channel.base-url}") String baseUrl,
            @Value("${app.channel.client-id}") String clientId,
            @Value("${app.channel.api-key}") String apiKey,
            ChannelInstanceId instanceId,
            ObjectMapper objectMapper) {
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.instanceId = instanceId;
        this.objectMapper = objectMapper;
    }

    JsonNode heartbeat(String appName, String startedAt, long uptimeSeconds) {
        return request("POST", "instances/heartbeat",
                Map.of("appName", appName, "startedAt", startedAt, "uptimeSeconds", uptimeSeconds));
    }

    void publishListings(List<Map<String, String>> listings) {
        request("PUT", "listings", listings);
    }

    void publishStock(List<StockLevel> stock) {
        List<Map<String, Object>> payload = stock.stream()
                .map(level -> Map.<String, Object>of(
                        "sellerSku", level.sellerSku(), "available", level.available()))
                .toList();
        request("PUT", "stock", payload);
    }

    JsonNode feed(long cursor, int limit) {
        return request("GET", "feed?after=" + cursor + "&limit=" + limit, null);
    }

    void decide(String orderId, String decision, String shopOrderId, String reason) {
        Map<String, String> payload = reason == null
                ? Map.of("decision", decision, "shopOrderId", shopOrderId)
                : Map.of("decision", decision, "shopOrderId", shopOrderId, "reason", reason);
        request("POST", "orders/" + encode(orderId) + "/decision", payload);
    }

    void confirmCancellation(String orderId) {
        request("POST", "orders/" + encode(orderId) + "/cancellation", Map.of("restocked", true));
    }

    void resolveBackorder(String orderId, String status) {
        request("POST", "orders/" + encode(orderId) + "/resolution", Map.of("status", status));
    }

    boolean configured() {
        return !clientId.isBlank() && !apiKey.isBlank();
    }

    private JsonNode request(String method, String path, Object payload) {
        if (clientId.isBlank() || apiKey.isBlank()) {
            throw new TianggeException("Tiangge credentials are not configured");
        }
        TianggeException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                        .timeout(Duration.ofSeconds(8))
                        .header("Accept", "application/json")
                        .header("X-Client-Id", clientId)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("X-Client-Instance", instanceId.value().toString());
                if (payload == null) {
                    builder.method(method, HttpRequest.BodyPublishers.noBody());
                } else {
                    builder.header("Content-Type", "application/json")
                            .method(method, HttpRequest.BodyPublishers.ofString(
                                    objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8));
                }
                HttpResponse<String> response = httpClient.send(
                        builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    if (response.body().isBlank()) {
                        return objectMapper.createObjectNode();
                    }
                    return objectMapper.readTree(response.body());
                }
                boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
                String message = "Tiangge request failed with HTTP " + response.statusCode()
                        + (response.body().isBlank() ? "" : ": " + response.body());
                lastFailure = new TianggeException(message, retryable);
                if (!retryable) {
                    throw lastFailure;
                }
            } catch (IOException ex) {
                lastFailure = new TianggeException("Tiangge transport or JSON failure", true, ex);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new TianggeException("Tiangge request was interrupted", true, ex);
            }
            if (attempt < MAX_ATTEMPTS) {
                pause(attempt);
            }
        }
        throw lastFailure == null ? new TianggeException("Tiangge request failed without a response") : lastFailure;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void pause(int attempt) {
        try {
            Thread.sleep(250L * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new TianggeException("Tiangge retry was interrupted", true, ex);
        }
    }
}

class TianggeException extends RuntimeException {
    private final boolean retryable;

    TianggeException(String message) {
        this(message, false);
    }

    TianggeException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    TianggeException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    boolean retryable() {
        return retryable;
    }
}
