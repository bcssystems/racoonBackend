package com.bcsystems.bonds.dto;

public record VentaDetalleRequest(
        Integer idProducto,
        String descripcion,
        String tipoDetalle,
        Integer idItemRapido,
        Integer cantidad,
        Double precioUnitario,
        Double subtotal,
        String atributosText,
        Double costoUnitario
) {}
