package com.bcsystems.bonds.dto;

import java.time.LocalDateTime;

public record CobranzaResponse(
        Integer idParcialidad,
        Integer numero,
        LocalDateTime fechaVencimiento,
        Double montoProgramado,
        Double montoPagado,
        Double saldo,
        String estado,
        Integer idCredito,
        String folioCredito,
        Integer idCliente,
        String clienteNombre,
        String telefono,
        String direccion,
        String periodicidad,
        Integer numeroParcialidades,
        Integer idCobrador,
        String cobradorNombre
) {}
