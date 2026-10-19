package com.bcsystems.bonds.dto;

import java.time.LocalDateTime;

public record SaldoFavorMovimientoResponse(
        Integer idMovimiento,
        Integer idCliente,
        String tipo,
        Double monto,
        Double saldoAnterior,
        Double saldoNuevo,
        Integer idVenta,
        Integer idVentaDetalle,
        String concepto,
        LocalDateTime fecha
) {}
