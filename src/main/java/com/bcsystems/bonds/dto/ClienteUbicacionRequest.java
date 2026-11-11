package com.bcsystems.bonds.dto;

public record ClienteUbicacionRequest(
        Double latitud,
        Double longitud,
        String precisionUbicacion
) {}
