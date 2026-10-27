package com.bcsystems.bonds.service.impl;

import com.bcsystems.bonds.domain.*;
import com.bcsystems.bonds.domain.en.*;
import com.bcsystems.bonds.dto.GastoRequest;
import com.bcsystems.bonds.dto.GastoResponse;
import com.bcsystems.bonds.exception.InvalidEntryException;
import com.bcsystems.bonds.exception.NotFoundException;
import com.bcsystems.bonds.repository.*;
import com.bcsystems.bonds.service.AuditoriaService;
import com.bcsystems.bonds.service.GastoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.core.Authentication;

@Service
@RequiredArgsConstructor
public class GastoServiceImpl implements GastoService {

    private final GastoRepository gastoRepository;
    private final CajaRepository cajaRepository;
    private final PersonaRepository personaRepository;
    private final AuditoriaService auditoriaService;

    @Override
    @Transactional
    public GastoResponse solicitar(GastoRequest request) {
        Caja caja = cajaRepository.findById(request.idCaja())
                .orElseThrow(() -> new NotFoundException("Caja no encontrada"));

        Persona usuario = obtenerPersonaActual();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean puedeAutorizar = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("GASTOS_AUTORIZAR"));
        LocalDateTime ahora = LocalDateTime.now();
        Gasto gasto = Gasto.builder()
                .caja(caja)
                .descripcion(request.descripcion())
                .monto(request.monto())
                .usuario(usuario)
                .estado(puedeAutorizar ? EstadoGasto.AUTORIZADO : EstadoGasto.PENDIENTE)
                .autorizador(puedeAutorizar ? usuario : null)
                .fechaCreacion(ahora)
                .fechaAutorizacion(puedeAutorizar ? ahora : null)
                .build();
        if (puedeAutorizar) {
            descontarDeCaja(caja, gasto.getMonto());
        }
        gasto = gastoRepository.save(gasto);
        auditoriaService.registrar("Gasto", gasto.getIdGasto(), "CREACION", usuario.getUsuario(),
                puedeAutorizar ? "Gasto creado y autorizado automáticamente" : "Gasto solicitado para autorización");

        return toResponse(gasto);
    }

    @Override
    public List<GastoResponse> pendientes() {
        return gastoRepository.findByEstadoOrderByFechaCreacionDesc(EstadoGasto.PENDIENTE).stream()
                .map(this::toResponse).toList();
    }

    @Override
    public List<GastoResponse> listarTodos() {
        return gastoRepository.findAllByOrderByFechaCreacionDesc().stream()
                .map(this::toResponse).toList();
    }

    @Override
    public List<GastoResponse> listarPorCaja(Integer idCaja) {
        return gastoRepository.findByCajaIdCajaOrderByFechaCreacionDesc(idCaja).stream()
                .map(this::toResponse).toList();
    }

    @Override
    public long contarPendientesPorCaja(Integer idCaja) {
        return gastoRepository.countByCajaIdCajaAndEstado(idCaja, EstadoGasto.PENDIENTE);
    }

    @Override
    @Transactional
    public GastoResponse autorizar(Integer idGasto) {
        Gasto gasto = gastoRepository.findById(idGasto)
                .orElseThrow(() -> new NotFoundException("Gasto no encontrado"));
        if (gasto.getEstado() != EstadoGasto.PENDIENTE) {
            throw new InvalidEntryException("El gasto no está pendiente");
        }

        Persona autorizador = obtenerPersonaActual();

        descontarDeCaja(gasto.getCaja(), gasto.getMonto());

        gasto.setEstado(EstadoGasto.AUTORIZADO);
        gasto.setAutorizador(autorizador);
        gasto.setFechaAutorizacion(LocalDateTime.now());
        gasto = gastoRepository.save(gasto);
        auditoriaService.registrar("Gasto", gasto.getIdGasto(), "ACTUALIZACION", autorizador.getUsuario(),
                "Gasto autorizado");

        return toResponse(gasto);
    }

    @Override
    @Transactional
    public GastoResponse rechazar(Integer idGasto) {
        Gasto gasto = gastoRepository.findById(idGasto)
                .orElseThrow(() -> new NotFoundException("Gasto no encontrado"));
        if (gasto.getEstado() != EstadoGasto.PENDIENTE) {
            throw new InvalidEntryException("El gasto no está pendiente");
        }

        Persona autorizador = obtenerPersonaActual();

        gasto.setEstado(EstadoGasto.RECHAZADO);
        gasto.setAutorizador(autorizador);
        gasto.setFechaAutorizacion(LocalDateTime.now());
        gasto = gastoRepository.save(gasto);

        return toResponse(gasto);
    }

    private Persona obtenerPersonaActual() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return personaRepository.findByUsuario(username)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    private void descontarDeCaja(Caja caja, double monto) {
        if (caja.getSaldoActual() < monto) {
            throw new InvalidEntryException("Saldo insuficiente en caja");
        }
        caja.setSaldoActual(caja.getSaldoActual() - monto);
        cajaRepository.save(caja);
    }

    private GastoResponse toResponse(Gasto g) {
        return new GastoResponse(
                g.getIdGasto(), g.getCaja().getIdCaja(),
                g.getCaja().getNombre(),
                g.getCaja().getSucursal() != null ? g.getCaja().getSucursal().getNombre() : null,
                g.getDescripcion(),
                g.getMonto(), g.getUsuario().getUsuario(),
                g.getAutorizador() != null ? g.getAutorizador().getUsuario() : null,
                g.getEstado().name(), g.getFechaCreacion(),
                g.getFechaAutorizacion());
    }
}
