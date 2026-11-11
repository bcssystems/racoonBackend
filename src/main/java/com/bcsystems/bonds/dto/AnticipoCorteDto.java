package com.bcsystems.bonds.dto;

import java.time.LocalDateTime;

public record AnticipoCorteDto(
        Integer idVenta,
        String cliente,
        LocalDateTime fecha,
        String tipoPago,
        Double monto
) {}
