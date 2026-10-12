package com.bcsystems.bonds.dto;

public record VentaDetalleResponse(
        Integer idVentaDetalle,
        Integer idProducto,
        String productoSku,
        String productoNombre,
        String descripcion,
        String tipoDetalle,
        Integer idItemRapido,
        Integer cantidad,
        String unidadMedida,
        Double precioUnitario,
        Double subtotal,
        Double costoUnitario,
        String atributosText
) {}
