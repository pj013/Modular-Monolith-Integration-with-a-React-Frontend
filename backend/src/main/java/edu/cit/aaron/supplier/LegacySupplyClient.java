package edu.cit.aaron.supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import edu.cit.aaron.channel.ChannelInstanceId;
import org.springframework.stereotype.Component;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Locale;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.w3c.dom.Document;
import javax.xml.parsers.ParserConfigurationException;

@Component
@SuppressWarnings("unused")
class LegacySupplyClient {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final URI baseUri;
    private final String clientId;
    private final String apiKey;
    private final ChannelInstanceId instanceId;
    private final Environment environment;
    private final HttpClient httpClient;
    private volatile String sessionToken;

    @SuppressWarnings("unused")
    LegacySupplyClient(
            @Value("${app.supplier.base-url:https://legacysupply.onrender.com/api/v1}") String baseUrl,
            @Value("${app.supplier.client-id:}") String clientId,
            @Value("${LS_API_KEY:}") String apiKey,
            ChannelInstanceId instanceId,
            Environment environment) {
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        this.clientId = clientId;
        this.apiKey = apiKey;
        this.instanceId = instanceId;
        this.environment = environment;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    SupplierAcknowledgement placeOrder(
            String productId, int requestedUnits, String buyerRef, String requestId) {
        SupplierTerms terms = termsFor(productId);
        int cases = casesFor(requestedUnits, terms.packSize());
        if (cases > 99) {
            throw new LegacySupplyException("Supplier order quantity exceeds 99 cases", false);
        }
        String body = "<PurchaseOrder><SupplierSku>" + xmlEscape(terms.supplierSku())
                + "</SupplierSku><Qty>" + cases + "</Qty><BuyerRef>" + xmlEscape(buyerRef)
                + "</BuyerRef></PurchaseOrder>";
        String response = execute("POST", "purchase-orders", body, requestId);
        String poNumber = xmlValue(response, "PoNumber");
        if (poNumber == null || poNumber.isBlank()) {
            throw new LegacySupplyException("Supplier acknowledgement did not include a PO number", true);
        }
        SupplierOrderStatus status = statusFromCode(xmlValue(response, "StatusCode"));
        return new SupplierAcknowledgement(poNumber, cases, cases * terms.packSize(), status);
    }

    SupplierOrderStatus getStatus(String poNumber) {
        String response = execute("GET", "purchase-orders/" + encodePathSegment(poNumber), null, null);
        String status = xmlValue(response, "Status");
        return statusFromCode(status == null ? xmlValue(response, "StatusCode") : status);
    }

    static int casesFor(int units, int packSize) {
        if (units < 1 || packSize < 1) {
            throw new IllegalArgumentException("Units and pack size must be positive");
        }
        return (units + packSize - 1) / packSize;
    }

    private SupplierTerms termsFor(String productId) {
        String prefix = "app.supplier.products." + productId;
        String supplierSku = environment.getProperty(prefix + ".sku");
        Integer packSize = environment.getProperty(prefix + ".pack-size", Integer.class);
        if (supplierSku == null || supplierSku.isBlank() || packSize == null || packSize < 1) {
            throw new LegacySupplyException("Supplier catalog mapping is missing for product " + productId, true);
        }
        return new SupplierTerms(supplierSku, packSize);
    }

    private String execute(String method, String path, String body, String requestId) {
        LegacySupplyException lastFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                String token = sessionToken();
                HttpResponse<String> response = send(method, path, body, token, requestId);
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body();
                }

                String code = xmlValue(response.body(), "Code");
                if (response.statusCode() == 401 && isSessionError(code)) {
                    sessionToken = null;
                    lastFailure = new LegacySupplyException("Supplier session expired", true);
                } else {
                    boolean retryable = response.statusCode() == 429 || response.statusCode() >= 500;
                    lastFailure = new LegacySupplyException(
                            "Supplier request failed (" + (code == null ? response.statusCode() : code) + ")", retryable);
                    if (!retryable) {
                        throw lastFailure;
                    }
                }
            } catch (LegacySupplyException ex) {
                lastFailure = ex;
                if (!ex.retryable()) {
                    throw ex;
                }
            }

            if (attempt < MAX_ATTEMPTS) {
                backoff(attempt);
            }
        }
        throw Objects.requireNonNull(lastFailure);
    }

    private String sessionToken() {
        String current = sessionToken;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (sessionToken != null) {
                return sessionToken;
            }
            if (clientId.isBlank() || apiKey.isBlank()) {
                throw new LegacySupplyException("Supplier credentials are not configured", true);
            }
            String body = "<AuthRequest><ClientId>" + xmlEscape(clientId) + "</ClientId><ApiKey>"
                    + xmlEscape(apiKey) + "</ApiKey></AuthRequest>";
            HttpResponse<String> response = send("POST", "auth/token", body, null, null);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String code = xmlValue(response.body(), "Code");
                throw new LegacySupplyException(
                        "Supplier authentication failed (" + (code == null ? response.statusCode() : code) + ")",
                        response.statusCode() == 429 || response.statusCode() >= 500);
            }
            String token = xmlValue(response.body(), "SessionToken");
            if (token == null || token.isBlank()) {
                throw new LegacySupplyException("Supplier authentication returned no session", true);
            }
            sessionToken = token;
            return token;
        }
    }

    private HttpResponse<String> send(String method, String path, String body, String token, String requestId) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(baseUri.resolve(path))
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "application/xml")
                    .header("X-Client-Instance", instanceId.value().toString());
            if (body != null) {
                builder.header("Content-Type", "application/xml; charset=UTF-8")
                        .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            if (token != null) {
                builder.header("X-LS-Session", token);
            }
            if (requestId != null) {
                builder.header("X-Request-Id", requestId);
            }
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new LegacySupplyException("Supplier transport failed", true, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LegacySupplyException("Supplier request was interrupted", true, ex);
        }
    }

    private static boolean isSessionError(String code) {
        return "E-AUTH-02".equals(code) || "E-AUTH-03".equals(code) || "E-AUTH-07".equals(code);
    }

    private static SupplierOrderStatus statusFromCode(String code) {
        if (code == null) {
            return SupplierOrderStatus.UNKNOWN;
        }
        return switch (code.trim().toUpperCase(Locale.ROOT)) {
            case "10" -> SupplierOrderStatus.ACCEPTED;
            case "20" -> SupplierOrderStatus.PICKING;
            case "30" -> SupplierOrderStatus.SHIPPED;
            case "40" -> SupplierOrderStatus.DELIVERED;
            case "90" -> SupplierOrderStatus.CANCELLED;
            case "CANCELLED", "CANCELED" -> SupplierOrderStatus.CANCELLED;
            default -> SupplierOrderStatus.UNKNOWN;
        };
    }

    private static String xmlValue(String xml, String tagName) {
        if (xml == null || xml.isBlank()) {
            return null;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            var nodes = document.getElementsByTagName(tagName);
            return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
        } catch (ParserConfigurationException | SAXException | IOException ex) {
            return null;
        }
    }

    private static String xmlEscape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    private static String encodePathSegment(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep(200L * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new LegacySupplyException("Supplier retry was interrupted", true, ex);
        }
    }
}

record SupplierTerms(String supplierSku, int packSize) {
}

record SupplierAcknowledgement(String poNumber, int cases, int units, SupplierOrderStatus status) {
}

class LegacySupplyException extends RuntimeException {
    private final boolean retryable;

    LegacySupplyException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    LegacySupplyException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    boolean retryable() {
        return retryable;
    }
}