package com.bcsystems.bonds.dto;

public record SolicitudCreditoRequest(
        Integer idCliente,
        Double montoSolicitado,
        Integer parcialidadesSolicitadas,
        String encuestaJson,
        String observaciones
) {}
