package com.avas.platform.commerce;

import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static com.avas.platform.commerce.CommerceModels.*;

@Service
public class CommerceService {
    private static final List<ProductEntity> DEFAULT_CATALOG = List.of(
            new ProductEntity("architect-review", "Architect concept review", "A licensed architect reviews one selected AVAS concept and records actionable notes.", "Design assurance", 15_000, "AR"),
            new ProductEntity("structural-review", "Structural feasibility review", "Preliminary grid, span and foundation assumptions reviewed by a structural professional.", "Engineering", 22_000, "SR"),
            new ProductEntity("site-consultation", "Site consultation", "One scheduled site visit with observations, photographs and an execution summary.", "Site services", 7_500, "SC"),
            new ProductEntity("approval-readiness", "Approval readiness pack", "Drawing checklist and submission-readiness review for the applicable local authority.", "Compliance", 12_000, "AP"),
            new ProductEntity("detailed-boq", "Detailed BOQ pack", "A trade-level quantity and rate pack derived from the approved conceptual drawing.", "Cost planning", 9_000, "BQ"),
            new ProductEntity("project-kickoff", "Professional project kickoff", "A coordinated kickoff call with scope, responsibilities, milestones and risk register.", "Coordination", 18_000, "PK")
    );

    private final ProductRepository products;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final RefundRepository refunds;
    private final WalletRepository wallets;
    private final WalletTransactionRepository walletTransactions;
    private final PaymentAuditRepository paymentAudits;
    private final RazorpayGateway gateway;

    public CommerceService(ProductRepository products, OrderRepository orders, PaymentRepository payments,
                           RefundRepository refunds, WalletRepository wallets,
                           WalletTransactionRepository walletTransactions,
                           PaymentAuditRepository paymentAudits, RazorpayGateway gateway) {
        this.products = products;
        this.orders = orders;
        this.payments = payments;
        this.refunds = refunds;
        this.wallets = wallets;
        this.walletTransactions = walletTransactions;
        this.paymentAudits = paymentAudits;
        this.gateway = gateway;
    }

    @PostConstruct
    void seedCatalog() {
        DEFAULT_CATALOG.stream().filter(product -> !products.existsByCode(product.getCode())).forEach(products::save);
    }

    @Transactional(readOnly = true)
    public List<Product> products() {
        return products.findAllByActiveTrueOrderByCategoryAscNameAsc().stream().map(this::toProduct).toList();
    }

    @Transactional
    public CheckoutSummary checkout(CheckoutRequest request, UUID userId, String tenantId) {
        var quantities = new LinkedHashMap<String, Integer>();
        request.items().forEach(line -> quantities.merge(line.productId(), line.quantity(), Integer::sum));
        if (quantities.values().stream().anyMatch(quantity -> quantity > 20)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A product quantity cannot exceed 20");
        }

        var resolved = new ArrayList<ResolvedLine>();
        for (var entry : quantities.entrySet()) {
            var product = products.findByCodeAndActiveTrue(entry.getKey())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown product: " + entry.getKey()));
            resolved.add(new ResolvedLine(product, entry.getValue()));
        }
        long total = resolved.stream().mapToLong(line -> Math.multiplyExact(line.product().getUnitPrice(), line.quantity())).sum();
        if (total <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The cart total must be greater than zero");

        var orderType = "TOPUP".equalsIgnoreCase(request.orderType()) ? OrderEntity.Type.TOPUP : OrderEntity.Type.REGULAR;
        var order = new OrderEntity(userId, tenantId, blankToNull(request.projectId()), orderType, total,
                request.buyerName().trim(), request.buyerEmail().trim().toLowerCase(), blankToNull(request.buyerPhone()));
        resolved.forEach(line -> order.addItem(new OrderItemEntity(order, line.product().getCode(), line.product().getName(),
                line.quantity(), line.product().getUnitPrice())));
        orders.save(order);

        var providerOrder = gateway.createOrder(order.getId().toString(), total);
        var payment = payments.save(new PaymentEntity(order, providerOrder.mode(), providerOrder.id(),
                providerOrder.publicKey(), providerOrder.checkoutReady()));
        audit(payment, "PAYMENT_CREATED", "Payment session created in " + providerOrder.mode() + " mode");
        return new CheckoutSummary(toOrder(order), toPayment(payment));
    }

    @Transactional(readOnly = true)
    public List<OrderReceipt> orders(UUID userId) {
        return orders.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(order ->
                new OrderReceipt(toOrder(order), toPayment(requiredPaymentForOrder(order.getId())))).toList();
    }

    @Transactional(readOnly = true)
    public OrderReceipt order(UUID orderId, UUID userId) {
        var order = requiredOwnedOrder(orderId, userId);
        return new OrderReceipt(toOrder(order), toPayment(requiredPaymentForOrder(orderId)));
    }

    @Transactional
    public OrderReceipt verify(VerifyPaymentRequest request, UUID userId) {
        var payment = requiredOwnedPayment(request.paymentSessionId(), userId);
        if (!payment.getGatewayOrderId().equals(request.gatewayOrderId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The gateway order does not match this payment session");
        }
        if (payment.getStatus() == PaymentEntity.Status.PAID) return order(payment.getOrderId(), userId);
        if (!gateway.verify(request.gatewayOrderId(), request.gatewayPaymentId(), request.signature())) {
            audit(payment, "PAYMENT_VERIFICATION_FAILED", "Gateway signature verification failed");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment signature verification failed");
        }

        var order = requiredOwnedOrder(payment.getOrderId(), userId);
        payment.paid(request.gatewayPaymentId());
        order.paid();
        if (order.getType() == OrderEntity.Type.TOPUP) creditWallet(userId, order.getTotal(), order.getId(), "Wallet top-up");
        audit(payment, "PAYMENT_PAID", "Payment signature verified and order marked paid");
        return new OrderReceipt(toOrder(order), toPayment(payment));
    }

    @Transactional
    public OrderReceipt simulate(UUID paymentSessionId, UUID userId) {
        var payment = requiredOwnedPayment(paymentSessionId, userId);
        if (!gateway.testMode() || !"TEST".equals(payment.getMode())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Test payment completion is disabled");
        }
        return verify(new VerifyPaymentRequest(payment.getId(), payment.getGatewayOrderId(),
                "test_payment_" + payment.getId().toString().replace("-", ""), "test_signature"), userId);
    }

    @Transactional
    public RefundResponse refund(UUID orderId, RefundRequest request, UUID userId) {
        var order = requiredOwnedOrder(orderId, userId);
        var payment = requiredPaymentForOrder(orderId);
        if (payment.getStatus() != PaymentEntity.Status.PAID || order.getStatus() != OrderEntity.Status.PAID) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a paid order can be refunded");
        }
        var refund = refunds.save(new RefundEntity(payment, userId, order.getTotal(), request.reason().trim()));
        var gatewayRefundId = gateway.refund(payment.getGatewayPaymentId(), order.getTotal(), request.reason());
        refund.succeeded(gatewayRefundId);
        payment.refunded();
        order.refunded();
        if (order.getType() == OrderEntity.Type.TOPUP) {
            var wallet = walletEntity(userId);
            try {
                wallet.debit(order.getTotal());
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Wallet funds have already been used; refund requires support review");
            }
            walletTransactions.save(new WalletTransactionEntity(wallet, WalletTransactionEntity.Type.REFUND_REVERSAL,
                    order.getTotal(), "ORDER", orderId.toString(), "Top-up refunded"));
        }
        audit(payment, "PAYMENT_REFUNDED", "Full refund completed: " + gatewayRefundId);
        return new RefundResponse(refund.getId(), orderId, refund.getAmount(), refund.getStatus().name(),
                refund.getReason(), refund.getGatewayRefundId());
    }

    @Transactional(readOnly = true)
    public WalletResponse wallet(UUID userId) {
        return wallets.findByUserId(userId).map(this::toWallet)
                .orElseGet(() -> new WalletResponse(null, 0, "INR", "ACTIVE", null));
    }

    @Transactional(readOnly = true)
    public List<WalletTransactionResponse> walletHistory(UUID userId) {
        return walletTransactions.findAllByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toWalletTransaction).toList();
    }

    @Transactional
    public CheckoutSummary createTopUp(WalletTopUpRequest request, UUID userId, String tenantId, String email) {
        var topUpProduct = new ProductEntity("wallet-topup", "AVAS wallet top-up", "Account wallet balance", "Wallet", request.amount(), "₹");
        var order = new OrderEntity(userId, tenantId, null, OrderEntity.Type.TOPUP, request.amount(), "AVAS customer", email, null);
        order.addItem(new OrderItemEntity(order, topUpProduct.getCode(), topUpProduct.getName(), 1, request.amount()));
        orders.save(order);
        var providerOrder = gateway.createOrder(order.getId().toString(), order.getTotal());
        var payment = payments.save(new PaymentEntity(order, providerOrder.mode(), providerOrder.id(), providerOrder.publicKey(), providerOrder.checkoutReady()));
        audit(payment, "WALLET_TOPUP_CREATED", "Wallet top-up payment created");
        return new CheckoutSummary(toOrder(order), toPayment(payment));
    }

    private void creditWallet(UUID userId, long amount, UUID orderId, String description) {
        var wallet = walletEntity(userId);
        wallet.credit(amount);
        walletTransactions.save(new WalletTransactionEntity(wallet, WalletTransactionEntity.Type.CREDIT, amount,
                "ORDER", orderId.toString(), description));
    }

    private WalletEntity walletEntity(UUID userId) {
        return wallets.findByUserId(userId).orElseGet(() -> wallets.save(new WalletEntity(userId)));
    }

    private OrderEntity requiredOwnedOrder(UUID id, UUID userId) {
        var order = orders.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        if (!order.getUserId().equals(userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        return order;
    }

    private PaymentEntity requiredOwnedPayment(UUID id, UUID userId) {
        var payment = payments.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment session not found"));
        if (!payment.getUserId().equals(userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment session not found");
        return payment;
    }

    private PaymentEntity requiredPaymentForOrder(UUID orderId) {
        return payments.findByOrderId(orderId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment session not found"));
    }

    private void audit(PaymentEntity payment, String event, String detail) {
        paymentAudits.save(new PaymentAuditEntity(payment, event, detail));
    }

    private Product toProduct(ProductEntity value) {
        return new Product(value.getCode(), value.getName(), value.getDescription(), value.getCategory(), value.getUnitPrice(), value.getIcon());
    }

    private CommerceOrder toOrder(OrderEntity value) {
        var lines = value.getItems().stream().map(item -> new OrderLine(item.getProductCode(), item.getProductName(),
                item.getQuantity(), item.getUnitPrice(), item.getLineTotal())).toList();
        return new CommerceOrder(value.getId(), value.getUserId(), value.getProjectId(), OrderStatus.valueOf(value.getStatus().name()),
                value.getType().name(), value.getCurrency(), value.getTotal(), lines, value.getBuyerName(), value.getBuyerEmail(),
                value.getBuyerPhone(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private PaymentSession toPayment(PaymentEntity value) {
        return new PaymentSession(value.getId(), value.getOrderId(), PaymentStatus.valueOf(value.getStatus().name()),
                value.getProvider(), value.getMode(), value.getGatewayOrderId(), value.getGatewayPaymentId(), value.getPublicKey(),
                value.getAmount(), value.getCurrency(), value.isCheckoutReady(), value.getCreatedAt(), value.getUpdatedAt());
    }

    private WalletResponse toWallet(WalletEntity value) {
        return new WalletResponse(value.getId(), value.getBalance(), value.getCurrency(), value.getStatus(), value.getUpdatedAt());
    }

    private WalletTransactionResponse toWalletTransaction(WalletTransactionEntity value) {
        return new WalletTransactionResponse(value.getId(), value.getType().name(), value.getAmount(), value.getBalanceAfter(),
                value.getReferenceType(), value.getReferenceId(), value.getDescription(), value.getCreatedAt());
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private record ResolvedLine(ProductEntity product, int quantity) {}
}
