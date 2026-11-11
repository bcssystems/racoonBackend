package com.bcsystems.bonds.dto;

import java.time.LocalDateTime;
import java.util.List;

public record CodigoPostalResponse(
        String codigoPostal,
        String estado,
        String municipio,
        List<String> colonias,
        String fuente,
        LocalDateTime consultadoEn,
        boolean desdeCache
) {}
