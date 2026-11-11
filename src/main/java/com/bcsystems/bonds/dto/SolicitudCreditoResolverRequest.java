package com.bcsystems.bonds.dto;

public record SolicitudCreditoResolverRequest(
        boolean aprobar,
        Double montoAprobado,
        Integer parcialidadesAprobadas,
        String observaciones
) {}
