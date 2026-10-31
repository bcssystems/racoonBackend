package com.bcsystems.bonds.dto;

import java.util.List;

public record CatalogoProductoResponse(
        Integer idProducto,
        String sku,
        String nombre,
        String descripcion,
        Double precio,
        String categoriaNombre,
        String imagenPrincipalUrl,
        List<CatalogoMultimediaResponse> multimedia
) {}
