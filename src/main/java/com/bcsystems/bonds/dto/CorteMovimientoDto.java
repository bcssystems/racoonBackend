package com.bcsystems.bonds.dto;

import java.time.LocalDateTime;

public record CorteMovimientoDto(
        Integer idMovimiento,
        LocalDateTime fecha,
        String tipo,
        String descripcion,
        Double monto,
        String usuario,
        String cliente,
        String folioCredito,
        Integer idVenta,
        Boolean cancelado,
        String motivoCancelacion
) {}
