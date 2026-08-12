package com.avas.platform.commerce;

public record OrderReceipt(CommerceOrder order, PaymentSession payment) {
}
