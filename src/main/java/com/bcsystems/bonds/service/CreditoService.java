package com.bcsystems.bonds.service;

import com.bcsystems.bonds.dto.*;

import java.util.List;

public interface CreditoService {

    List<CreditoResponse> listarCreditosPorCliente(Integer idCliente);

    List<MovimientoCreditoResponse> listarMovimientosPorCliente(Integer idCliente);

    AbonoResponse registrarAbono(AbonoRequest request);

    List<AbonoResponse> abonarATodas(AbonoGeneralRequest request);

    void condonarIntereses(Integer idCredito, CondonacionInteresRequest request);

    List<ParcialidadResponse> listarParcialidades(Integer idCredito);

    EstadoCuentaResponse estadoCuenta(Integer idCredito);
}
