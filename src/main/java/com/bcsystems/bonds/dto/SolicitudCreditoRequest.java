package com.bcsystems.bonds.dto;

public record SolicitudCreditoRequest(
        Integer idCliente,
        Double montoSolicitado,
        String encuestaJson,
        String observaciones
) {}
