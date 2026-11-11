package com.bcsystems.bonds.dto;

import java.time.LocalDateTime;

public record ClienteUbicacionResponse(
        Integer idCliente,
        Double latitud,
        Double longitud,
        String direccionNormalizada,
        String precisionUbicacion,
        String estadoGeocodificacion,
        LocalDateTime geocodificadoEn
) {}
