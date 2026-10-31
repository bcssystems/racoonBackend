package com.bcsystems.bonds.dto;

public record CatalogoMultimediaResponse(
        Integer idMultimedia,
        String tipo,
        String url,
        String nombreArchivo,
        Boolean esPrincipal,
        Integer orden
) {}
