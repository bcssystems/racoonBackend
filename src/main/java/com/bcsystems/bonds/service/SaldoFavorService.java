package com.bcsystems.bonds.service;

import com.bcsystems.bonds.dto.SaldoFavorIngresoRequest;
import com.bcsystems.bonds.dto.SaldoFavorMovimientoResponse;

import java.util.List;

public interface SaldoFavorService {
    SaldoFavorMovimientoResponse ingresar(Integer idCliente, SaldoFavorIngresoRequest request);
    List<SaldoFavorMovimientoResponse> listarMovimientos(Integer idCliente);
}
