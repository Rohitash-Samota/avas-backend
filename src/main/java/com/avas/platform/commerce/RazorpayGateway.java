package com.avas.platform.commerce;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

@Component
class RazorpayGateway {
    record GatewayOrder(String id, String mode, String publicKey, boolean checkoutReady) {
    }

    private final String keyId;
    private final String keySecret;
    private final boolean testMode;
    private final RestClient client;

    RazorpayGateway(
            @Value("${avas.commerce.razorpay.key-id:}") String keyId,
            @Value("${avas.commerce.razorpay.key-secret:}") String keySecret,
            @Value("${avas.commerce.razorpay.api-url:https://api.razorpay.com/v1}") String apiUrl,
            @Value("${avas.commerce.test-mode:true}") boolean testMode) {
        this.keyId = keyId == null ? "" : keyId.trim();
        this.keySecret = keySecret == null ? "" : keySecret.trim();
        this.testMode = testMode;
        this.client = RestClient.builder().baseUrl(apiUrl).build();
    }

    GatewayOrder createOrder(String receipt, long amount) {
        if (!configured()) {
            if (!testMode) {
                throw new ResponseStatusException(BAD_GATEWAY, "Razorpay credentials are not configured");
            }
            return new GatewayOrder("test_order_" + receipt.replace("-", ""), "TEST", "rzp_test_local", false);
        }

        try {
            var response = client.post()
                    .uri("/orders")
                    .headers(headers -> headers.setBasicAuth(keyId, keySecret))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "amount", Math.multiplyExact(amount, 100),
                            "currency", "INR",
                            "receipt", receipt,
                            "payment_capture", 1))
                    .retrieve()
                    .body(JsonNode.class);
            var gatewayId = response == null ? "" : response.path("id").asText("");
            if (gatewayId.isBlank()) {
                throw new IllegalStateException("Razorpay did not return an order id");
            }
            return new GatewayOrder(gatewayId, "LIVE", keyId, true);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Unable to create the Razorpay order", exception);
        }
    }

    boolean verify(String gatewayOrderId, String gatewayPaymentId, String signature) {
        if (!configured()) {
            return testMode && "test_signature".equals(signature) && gatewayOrderId.startsWith("test_order_")
                    && gatewayPaymentId.startsWith("test_payment_");
        }
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var expected = HexFormat.of().formatHex(mac.doFinal(
                    (gatewayOrderId + "|" + gatewayPaymentId).getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to verify the payment signature", exception);
        }
    }

    String refund(String gatewayPaymentId, long amount, String reason) {
        if (!configured()) {
            if (!testMode)
                throw new ResponseStatusException(BAD_GATEWAY, "Razorpay credentials are not configured");
            return "test_refund_" + gatewayPaymentId.replace("-", "");
        }
        try {
            var response = client.post()
                    .uri("/payments/{paymentId}/refund", gatewayPaymentId)
                    .headers(headers -> headers.setBasicAuth(keyId, keySecret))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", Math.multiplyExact(amount, 100), "notes", Map.of("reason", reason)))
                    .retrieve()
                    .body(JsonNode.class);
            var refundId = response == null ? "" : response.path("id").asText("");
            if (refundId.isBlank())
                throw new IllegalStateException("Razorpay did not return a refund id");
            return refundId;
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Unable to refund the Razorpay payment", exception);
        }
    }

    boolean testMode() {
        return testMode && !configured();
    }

    private boolean configured() {
        return !keyId.isBlank() && !keySecret.isBlank();
    }
}
