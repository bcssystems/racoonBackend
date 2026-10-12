package com.bcsystems.bonds.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ProductoRequest(
    String sku,
    @NotBlank String nombre,
    String descripcion,
    Double precioBase,
    Double precio2,
    Double precio3,
    Double precio4,
    Double costoPromedio,
    @NotNull Integer idCategoria,
    String unidadMedida,
    Boolean activo,
    List<InventarioSucursalRequest> inventarios
) {}
