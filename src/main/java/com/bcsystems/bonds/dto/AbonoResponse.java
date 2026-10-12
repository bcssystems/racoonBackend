package com.bcsystems.bonds.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AbonoResponse(
        Integer idAbono,
        Integer idCredito,
        Double monto,
        String tipo,
        LocalDateTime fecha,
        String usuario,
        String metodoPago,
        List<AbonoPagoResponse> pagos,
        Double saldoAnterior,
        Double saldoNuevo,
        String estadoCredito,
        String clienteNombre,
        Integer idVenta,
        String folioCredito,
        Double interesPagado,
        Double capitalPagado,
        Double saldoInteres,
        Double saldoCapital
) {}
