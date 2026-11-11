package com.bcsystems.bonds.service;

import com.bcsystems.bonds.dto.ClienteUbicacionRequest;
import com.bcsystems.bonds.dto.ClienteUbicacionResponse;

public interface GeocodificacionService {
    ClienteUbicacionResponse geocodificar(Integer idCliente);
    ClienteUbicacionResponse actualizar(Integer idCliente, ClienteUbicacionRequest request);
}
