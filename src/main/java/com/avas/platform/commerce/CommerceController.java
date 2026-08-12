package com.avas.platform.commerce;

import com.avas.platform.auth.AvasPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/commerce")
public class CommerceController {
    private final CommerceService service;

    public CommerceController(CommerceService service) { this.service = service; }

    @GetMapping("/products")
    List<Product> products() { return service.products(); }

    @PostMapping("/checkout")
    ResponseEntity<CheckoutSummary> checkout(@Valid @RequestBody CheckoutRequest request,
                                             @AuthenticationPrincipal AvasPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.checkout(request, principal.userId(), principal.tenantId()));
    }

    @GetMapping("/orders")
    List<OrderReceipt> orders(@AuthenticationPrincipal AvasPrincipal principal) { return service.orders(principal.userId()); }

    @GetMapping("/orders/{orderId}")
    OrderReceipt order(@PathVariable UUID orderId, @AuthenticationPrincipal AvasPrincipal principal) {
        return service.order(orderId, principal.userId());
    }

    @PostMapping("/payments/verify")
    OrderReceipt verify(@Valid @RequestBody VerifyPaymentRequest request, @AuthenticationPrincipal AvasPrincipal principal) {
        return service.verify(request, principal.userId());
    }

    @PostMapping("/payments/{paymentSessionId}/simulate")
    OrderReceipt simulate(@PathVariable UUID paymentSessionId, @AuthenticationPrincipal AvasPrincipal principal) {
        return service.simulate(paymentSessionId, principal.userId());
    }

    @PostMapping("/orders/{orderId}/refund")
    RefundResponse refund(@PathVariable UUID orderId, @Valid @RequestBody RefundRequest request,
                          @AuthenticationPrincipal AvasPrincipal principal) {
        return service.refund(orderId, request, principal.userId());
    }

    @GetMapping("/wallet")
    WalletResponse wallet(@AuthenticationPrincipal AvasPrincipal principal) { return service.wallet(principal.userId()); }

    @GetMapping("/wallet/history")
    List<WalletTransactionResponse> history(@AuthenticationPrincipal AvasPrincipal principal) {
        return service.walletHistory(principal.userId());
    }

    @PostMapping("/wallet/topups")
    ResponseEntity<CheckoutSummary> topUp(@Valid @RequestBody WalletTopUpRequest request,
                                          @AuthenticationPrincipal AvasPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createTopUp(request, principal.userId(), principal.tenantId(), principal.email()));
    }
}
