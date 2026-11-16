package com.bcsystems.bonds.dto;

public record ProgresoCobradorResponse(
        Integer idCobrador,
        String cobrador,
        Double meta,
        Double cobrado,
        Double porcentaje,
        Double restante
) {}
