package com.avas.platform.commerce;

public record CheckoutSummary(CommerceOrder order, PaymentSession payment) {
}
