package com.bcsystems.bonds.dto;

import com.bcsystems.bonds.domain.en.EstadoCredito;

import java.time.LocalDateTime;

public record CreditoResponse(
        Integer idCredito,
        Integer idVenta,
        Integer folioVenta,
        String folio,
        Integer idCliente,
        String clienteNombre,
        Double montoOriginal,
        Double saldoPendiente,
        Integer plazoMeses,
        Double porcentajeInteres,
        String periodicidad,
        Integer numeroParcialidades,
        LocalDateTime fechaPrimerPago,
        Double capitalFinanciado,
        Double interesTotal,
        Double saldoCapital,
        Double saldoInteres,
        LocalDateTime fechaVencimiento,
        EstadoCredito estado,
        LocalDateTime fechaCreacion,
        String nota,
        Integer idCobrador,
        String cobradorNombre
) {}
