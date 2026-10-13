package com.bcsystems.bonds.service;

import com.bcsystems.bonds.dto.*;

import java.util.List;
import java.time.LocalDate;

public interface CreditoService {

    List<CreditoResponse> listarCreditosPorCliente(Integer idCliente);

    List<MovimientoCreditoResponse> listarMovimientosPorCliente(Integer idCliente);

    AbonoResponse registrarAbono(AbonoRequest request);

    List<AbonoResponse> abonarATodas(AbonoGeneralRequest request);

    void condonarIntereses(Integer idCredito, CondonacionInteresRequest request);

    List<ParcialidadResponse> listarParcialidades(Integer idCredito);

    EstadoCuentaResponse estadoCuenta(Integer idCredito);

    List<CobradorResponse> listarCobradores();

    void asignarCobrador(Integer idCredito, Integer idPersona);

    List<CobranzaResponse> agendaCobranza(LocalDate desde, LocalDate hasta, Integer idCobrador, String estado);

    AbonoResponse registrarAbonoCobranza(AbonoRequest request);
}
