package com.bcsystems.bonds.dto;

import java.time.LocalDateTime;

public record ProductoMultimediaResponse(
        Integer idMultimedia,
        Integer idProducto,
        String tipo,
        String url,
        String nombreArchivo,
        Boolean esPrincipal,
        Integer orden,
        LocalDateTime fechaSubida
) {}
