package com.avas.platform.commerce;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

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
