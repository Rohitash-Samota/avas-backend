package com.avas.platform.commerce;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CommerceModels {
    private CommerceModels() {}

    public enum OrderStatus { PENDING_PAYMENT, PAID, CANCELLED, REFUNDED }
    public enum PaymentStatus { CREATED, PAID, FAILED, REFUNDED }

    public record Product(String id, String name, String description, String category, long unitPrice, String icon) {}

    public record CartLineRequest(@NotBlank String productId, @Min(1) @Max(20) int quantity) {}

    public record CheckoutRequest(
            @NotEmpty List<@Valid CartLineRequest> items,
            @NotBlank String buyerName,
            @NotBlank @Email String buyerEmail,
            String buyerPhone,
            String projectId,
            String orderType
    ) {
        public CheckoutRequest {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record OrderLine(String productId, String name, int quantity, long unitPrice, long lineTotal) {}

    public record CommerceOrder(
            UUID id,
            UUID userId,
            String projectId,
            OrderStatus status,
            String orderType,
            String currency,
            long total,
            List<OrderLine> lines,
            String buyerName,
            String buyerEmail,
            String buyerPhone,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record PaymentSession(
            UUID id,
            UUID orderId,
            PaymentStatus status,
            String provider,
            String mode,
            String gatewayOrderId,
            String gatewayPaymentId,
            String publicKey,
            long amount,
            String currency,
            boolean checkoutReady,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CheckoutSummary(CommerceOrder order, PaymentSession payment) {}

    public record VerifyPaymentRequest(
            @NotNull UUID paymentSessionId,
            @NotBlank String gatewayOrderId,
            @NotBlank String gatewayPaymentId,
            @NotBlank String signature
    ) {}

    public record RefundRequest(@NotBlank @Size(max = 500) String reason) {}
    public record RefundResponse(UUID id, UUID orderId, long amount, String status, String reason, String gatewayRefundId) {}
    public record WalletTopUpRequest(@Min(100) @Max(10_000_000) long amount) {}
    public record WalletResponse(UUID id, long balance, String currency, String status, Instant updatedAt) {}
    public record WalletTransactionResponse(
            UUID id, String type, long amount, long balanceAfter, String referenceType,
            String referenceId, String description, Instant createdAt
    ) {}
    public record OrderReceipt(CommerceOrder order, PaymentSession payment) {}
}
