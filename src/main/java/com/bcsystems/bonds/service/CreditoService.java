package com.bcsystems.bonds.service;

import com.bcsystems.bonds.dto.*;

import java.util.List;
import java.time.LocalDate;
import org.springframework.data.domain.Page;

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

    Page<CobranzaResponse> agendaCobranza(LocalDate desde, LocalDate hasta, Integer idCobrador, Boolean miCartera, String estado, String cliente, int page, int size);

    AbonoResponse registrarAbonoCobranza(AbonoRequest request);
}
