package com.bcsystems.bonds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CondonacionInteresRequest(
        @NotNull Double monto,
        @NotBlank String motivo
) {}
