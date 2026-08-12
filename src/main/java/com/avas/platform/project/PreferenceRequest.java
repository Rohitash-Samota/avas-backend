package com.avas.platform.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record PreferenceRequest(
        @NotNull @Size(min = 1, max = 5) List<@NotBlank String> preferences
) {}
