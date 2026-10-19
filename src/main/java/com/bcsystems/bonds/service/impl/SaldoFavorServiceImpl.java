package com.bcsystems.bonds.service.impl;

import com.bcsystems.bonds.domain.*;
import com.bcsystems.bonds.domain.en.CajaEstado;
import com.bcsystems.bonds.domain.en.TipoMovimientoCaja;
import com.bcsystems.bonds.dto.SaldoFavorIngresoRequest;
import com.bcsystems.bonds.dto.SaldoFavorMovimientoResponse;
import com.bcsystems.bonds.exception.InvalidEntryException;
import com.bcsystems.bonds.exception.NotFoundException;
import com.bcsystems.bonds.repository.*;
import com.bcsystems.bonds.service.SaldoFavorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SaldoFavorServiceImpl implements SaldoFavorService {
    private final ClienteRepository clienteRepository;
    private final CajaRepository cajaRepository;
    private final TipoPagoRepository tipoPagoRepository;
    private final PersonaRepository personaRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final SaldoFavorMovimientoRepository saldoFavorMovimientoRepository;

    @Override
    @Transactional
    public SaldoFavorMovimientoResponse ingresar(Integer idCliente, SaldoFavorIngresoRequest request) {
        Cliente cliente = clienteRepository.findById(idCliente)
                .orElseThrow(() -> new NotFoundException("Cliente no encontrado"));
        Caja caja = cajaRepository.findById(request.idCaja())
                .orElseThrow(() -> new NotFoundException("Caja no encontrada"));
        if (caja.getEstado() != CajaEstado.ABIERTA) {
            throw new InvalidEntryException("La caja debe estar abierta");
        }
        tipoPagoRepository.findById(request.idTipoPago())
                .orElseThrow(() -> new NotFoundException("Tipo de pago no encontrado"));
        Persona usuario = obtenerPersonaActual();
        double anterior = cliente.getSaldoFavor() == null ? 0 : cliente.getSaldoFavor();
        double nuevo = anterior + request.monto();
        LocalDateTime ahora = LocalDateTime.now();

        cliente.setSaldoFavor(nuevo);
        clienteRepository.save(cliente);
        caja.setSaldoActual((caja.getSaldoActual() == null ? 0 : caja.getSaldoActual()) + request.monto());
        cajaRepository.save(caja);
        movimientoCajaRepository.save(MovimientoCaja.builder()
                .caja(caja).tipo(TipoMovimientoCaja.INGRESO).monto(request.monto())
                .motivo("Anticipo / saldo a favor del cliente #" + idCliente)
                .usuario(usuario).fecha(ahora).build());
        SaldoFavorMovimiento movimiento = saldoFavorMovimientoRepository.save(SaldoFavorMovimiento.builder()
                .cliente(cliente).tipo("INGRESO").monto(request.monto())
                .saldoAnterior(anterior).saldoNuevo(nuevo).usuario(usuario)
                .concepto(request.concepto() == null || request.concepto().isBlank() ? "Anticipo" : request.concepto())
                .fecha(ahora).build());
        return toResponse(movimiento);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SaldoFavorMovimientoResponse> listarMovimientos(Integer idCliente) {
        if (!clienteRepository.existsById(idCliente)) throw new NotFoundException("Cliente no encontrado");
        return saldoFavorMovimientoRepository.findByClienteIdClienteOrderByFechaDesc(idCliente)
                .stream().map(this::toResponse).toList();
    }

    private Persona obtenerPersonaActual() {
        String usuario = SecurityContextHolder.getContext().getAuthentication().getName();
        return personaRepository.findByUsuarioIgnoreCase(usuario)
                .orElseThrow(() -> new NotFoundException("Usuario actual no encontrado"));
    }

    private SaldoFavorMovimientoResponse toResponse(SaldoFavorMovimiento m) {
        return new SaldoFavorMovimientoResponse(m.getIdMovimiento(), m.getCliente().getIdCliente(), m.getTipo(),
                m.getMonto(), m.getSaldoAnterior(), m.getSaldoNuevo(),
                m.getVenta() == null ? null : m.getVenta().getIdVenta(),
                m.getVentaDetalle() == null ? null : m.getVentaDetalle().getIdVentaDetalle(),
                m.getConcepto(), m.getFecha());
    }
}
