package com.bcsystems.bonds.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SaldoFavorIngresoRequest(
        @NotNull @Positive Double monto,
        @NotNull Integer idCaja,
        @NotNull Integer idTipoPago,
        String referencia,
        String concepto
) {}
