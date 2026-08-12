package com.avas.platform.pricing;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PriceSubmissionDecisionRequest(
        @NotNull @JsonAlias("status") PriceSubmissionStatus decision,
        @Size(max = 1000) String note
) {
}
