package com.bcsystems.bonds.dto;

import java.time.LocalDateTime;

public record MovimientoCajaResponse(
        Integer idMovimientoCaja,
        Integer idCaja,
        String cajaNombre,
        String tipo,
        Double monto,
        String motivo,
        String usuario,
        LocalDateTime fecha,
        Integer idAbono,
        Integer idCredito,
        String folioCredito,
        String clienteNombre,
        Double saldoAnterior,
        Double interesPagado,
        Double capitalPagado,
        Double saldoNuevo,
        Double saldoInteres,
        Double saldoCapital,
        String estadoCredito
) {}
