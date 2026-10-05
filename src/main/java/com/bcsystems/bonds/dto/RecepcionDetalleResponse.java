package com.bcsystems.bonds.dto;

public record RecepcionDetalleResponse(
        Integer idRecepcionDetalle,
        Integer idProducto,
        String productoNombre,
        String sku,
        Integer cantidad,
        Double precioCompra,
        Double subtotal
) {}
