package com.bcsystems.bonds.dto;

public record SolicitudCreditoResolverRequest(
        boolean aprobar,
        Double montoAprobado,
        String observaciones
) {}
