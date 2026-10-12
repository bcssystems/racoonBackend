package com.bcsystems.bonds.dto;

public record ProductoVentaProjection(
    Integer idProducto,
    String sku,
    String nombre,
    Double precioBase,
    Double precio2,
    Double precio3,
    Double precio4,
    Integer stockActual,
    Double costoPromedio,
    Boolean activo,
    String unidadMedida
) {
}
