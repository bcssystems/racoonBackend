package com.bcsystems.bonds.service.impl;

import com.bcsystems.bonds.domain.*;
import com.bcsystems.bonds.domain.en.*;
import com.bcsystems.bonds.dto.*;
import com.bcsystems.bonds.exception.InvalidEntryException;
import com.bcsystems.bonds.exception.NotFoundException;
import com.bcsystems.bonds.repository.*;
import com.bcsystems.bonds.service.AuditoriaService;
import com.bcsystems.bonds.service.CajaService;
import com.bcsystems.bonds.service.WhatsAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CajaServiceImpl implements CajaService {

    private final CajaRepository cajaRepository;
    private final SucursalRepository sucursalRepository;
    private final CorteCajaRepository corteCajaRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final VentaRepository ventaRepository;
    private final PersonaRepository personaRepository;
    private final AuditoriaService auditoriaService;
    private final VentaPagoRepository ventaPagoRepository;
    private final CorteDetallePagoRepository corteDetallePagoRepository;
    private final TipoPagoRepository tipoPagoRepository;
    private final GastoRepository gastoRepository;
    private final AbonoRepository abonoRepository;
    private final AbonoPagoRepository abonoPagoRepository;
    private final ParcialidadCreditoRepository parcialidadCreditoRepository;
    private final ClienteRepository clienteRepository;
    private final CreditoRepository creditoRepository;
    private final com.bcsystems.bonds.service.ScoreCreditoService scoreCreditoService;
    private final WhatsAppService whatsAppService;

    @Override
    public List<CajaResponse> listar() {
        return cajaRepository.findByActivaTrueOrderByNombre().stream()
                .map(this::toResponse).toList();
    }

    @Override
    public List<CajaResponse> listarPorSucursal(Integer idSucursal) {
        return cajaRepository.findBySucursalIdSucursalAndActivaTrue(idSucursal).stream()
                .map(this::toResponse).toList();
    }

    @Override
    public CajaResponse obtenerPorId(Integer id) {
        return toResponse(buscarOExcepcion(id));
    }

    @Override
    @Transactional
    public CajaResponse crear(CajaRequest request) {
        Sucursal sucursal = sucursalRepository.findById(request.idSucursal())
                .orElseThrow(() -> new NotFoundException("Sucursal no encontrada"));

        TipoCaja tipo = request.tipo() != null ? TipoCaja.valueOf(request.tipo()) : TipoCaja.PRINCIPAL;
        validarCajaUnica(sucursal, tipo, null);

        Caja caja = Caja.builder()
                .nombre(request.nombre())
                .tipo(tipo)
                .sucursal(sucursal)
                .estado(CajaEstado.CERRADA)
                .saldoActual(0.0)
                .activa(true)
                .build();
        caja = cajaRepository.save(caja);

        String usuario = obtenerUsuarioActual();
        auditoriaService.registrar("Caja", caja.getIdCaja(), AccionAuditoria.CREACION.name(),
                usuario, "Caja creada: " + caja.getNombre());

        return toResponse(caja);
    }

    @Override
    @Transactional
    public CajaResponse actualizar(Integer id, CajaRequest request) {
        Caja caja = buscarOExcepcion(id);
        Sucursal sucursal = sucursalRepository.findById(request.idSucursal())
                .orElseThrow(() -> new NotFoundException("Sucursal no encontrada"));
        TipoCaja tipo = request.tipo() != null ? TipoCaja.valueOf(request.tipo()) : TipoCaja.PRINCIPAL;
        validarCajaUnica(sucursal, tipo, id);
        caja.setNombre(request.nombre());
        caja.setSucursal(sucursal);
        caja.setTipo(tipo);
        caja = cajaRepository.save(caja);

        String usuario = obtenerUsuarioActual();
        auditoriaService.registrar("Caja", id, AccionAuditoria.ACTUALIZACION.name(),
                usuario, "Caja actualizada: " + caja.getNombre());

        return toResponse(caja);
    }

    @Override
    @Transactional
    public void eliminar(Integer id) {
        Caja caja = buscarOExcepcion(id);
        caja.setActiva(false);
        cajaRepository.save(caja);
    }

    @Override
    @Transactional
    public CajaResponse abrirCaja(Integer id, AperturaCajaRequest request) {
        Caja caja = buscarOExcepcion(id);
        if (caja.getEstado() == CajaEstado.ABIERTA) {
            throw new InvalidEntryException("La caja ya está abierta");
        }
        if (request.saldoInicial() < 0) {
            throw new InvalidEntryException("El saldo inicial no puede ser negativo");
        }
        caja.setEstado(CajaEstado.ABIERTA);
        caja.setSaldoActual(request.saldoInicial());
        caja.setFechaApertura(LocalDateTime.now());
        caja.setFechaCierre(null);
        caja = cajaRepository.save(caja);

        String usuario = obtenerUsuarioActual();
        auditoriaService.registrar("Caja", id, AccionAuditoria.CREACION.name(),
                usuario, "Caja abierta con saldo inicial: $" + request.saldoInicial());

        return toResponse(caja);
    }

    @Override
    @Transactional
    public CajaResponse cerrarCaja(Integer id) {
        Caja caja = buscarOExcepcion(id);
        if (caja.getEstado() == CajaEstado.CERRADA) {
            throw new InvalidEntryException("La caja ya está cerrada");
        }
        caja.setEstado(CajaEstado.CERRADA);
        caja.setFechaCierre(LocalDateTime.now());
        caja = cajaRepository.save(caja);

        String usuario = obtenerUsuarioActual();
        auditoriaService.registrar("Caja", id, AccionAuditoria.ACTUALIZACION.name(),
                usuario, "Caja cerrada");

        return toResponse(caja);
    }

    @Override
    @Transactional
    public CajaResponse ingresarEfectivo(Integer id, MovimientoCajaRequest request) {
        Caja caja = buscarOExcepcion(id);
        validarCajaAbierta(caja);

        MovimientoCaja mov = MovimientoCaja.builder()
                .caja(caja)
                .tipo(TipoMovimientoCaja.INGRESO)
                .monto(request.monto())
                .motivo(request.motivo())
                .usuario(obtenerPersonaActual())
                .fecha(LocalDateTime.now())
                .build();
        movimientoCajaRepository.save(mov);

        caja.setSaldoActual(caja.getSaldoActual() + request.monto());
        caja = cajaRepository.save(caja);

        return toResponse(caja);
    }

    @Override
    @Transactional
    public CajaResponse retirarEfectivo(Integer id, MovimientoCajaRequest request) {
        Caja caja = buscarOExcepcion(id);
        validarCajaAbierta(caja);

        if (caja.getSaldoActual() < request.monto()) {
            throw new InvalidEntryException("Saldo insuficiente en caja");
        }

        MovimientoCaja mov = MovimientoCaja.builder()
                .caja(caja)
                .tipo(TipoMovimientoCaja.EGRESO)
                .monto(request.monto())
                .motivo(request.motivo())
                .usuario(obtenerPersonaActual())
                .fecha(LocalDateTime.now())
                .build();
        movimientoCajaRepository.save(mov);

        caja.setSaldoActual(caja.getSaldoActual() - request.monto());
        caja = cajaRepository.save(caja);

        return toResponse(caja);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovimientoCajaResponse> movimientos(Integer id) {
        return movimientoCajaRepository.findByCajaIdCajaOrderByFechaDesc(id).stream()
                .map(this::toMovimientoResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MovimientoCajaResponse> solicitudesCancelacion() {
        return movimientoCajaRepository.findByFechaSolicitudCancelacionIsNotNullAndCanceladoFalseOrderByFechaSolicitudCancelacionDesc()
                .stream().map(this::toMovimientoResponse).toList();
    }

    private MovimientoCajaResponse toMovimientoResponse(MovimientoCaja m) {
                    Abono abono = m.getAbono();
                    Credito credito = abono == null ? null : abono.getCredito();
                    Cliente cliente = credito == null ? null : credito.getCliente();
                    Double saldoAnterior = abono == null ? null :
                            (credito.getSaldoPendiente() == null ? null : credito.getSaldoPendiente() + abono.getMonto());
                    return new MovimientoCajaResponse(
                            m.getIdMovimientoCaja(), m.getCaja().getIdCaja(),
                            m.getCaja().getNombre(), m.getTipo().name(),
                            m.getMonto(), m.getMotivo(), m.getUsuario().getUsuario(), m.getFecha(),
                            abono == null ? null : abono.getIdAbono(),
                            credito == null ? null : credito.getIdCredito(),
                            credito == null ? null : credito.getFolio(),
                            cliente == null ? null : cliente.getNombre() + " " +
                                    (cliente.getApellidoPaterno() == null ? "" : cliente.getApellidoPaterno()),
                            saldoAnterior,
                            abono == null ? null : abono.getInteresPagado(),
                            abono == null ? null : abono.getCapitalPagado(),
                            credito == null ? null : credito.getSaldoPendiente(),
                            credito == null ? null : credito.getSaldoInteres(),
                            credito == null ? null : credito.getSaldoCapital(),
                             credito == null || credito.getEstado() == null ? null : credito.getEstado().name(),
                             Boolean.TRUE.equals(m.getCancelado()),
                             m.getFechaSolicitudCancelacion() == null ? null : "SOLICITADA",
                             m.getMotivoCancelacion(),
                             m.getVenta() == null ? null : m.getVenta().getIdVenta());
    }

    @Override
    @Transactional
    public void solicitarCancelacionMovimiento(Integer idMovimiento, String motivo) {
        MovimientoCaja movimiento = movimientoCajaRepository.findById(idMovimiento)
                .orElseThrow(() -> new NotFoundException("Movimiento de caja no encontrado"));
        if (Boolean.TRUE.equals(movimiento.getCancelado())) throw new InvalidEntryException("El movimiento ya está cancelado");
        if (motivo == null || motivo.isBlank()) throw new InvalidEntryException("Debe indicar un motivo de cancelación");
        if (movimiento.getFechaSolicitudCancelacion() != null) throw new InvalidEntryException("El movimiento ya tiene una solicitud pendiente");
        Persona solicitante = obtenerPersonaActual();
        movimiento.setMotivoCancelacion(motivo.trim());
        movimiento.setSolicitanteCancelacion(solicitante);
        movimiento.setFechaSolicitudCancelacion(LocalDateTime.now());
        movimientoCajaRepository.save(movimiento);
        auditoriaService.registrar("MovimientoCaja", idMovimiento, "ACTUALIZACION", solicitante.getUsuario(),
                "Solicitud de cancelación: " + motivo.trim());
    }

    @Override
    @Transactional
    public CodigoAutorizacionResponse generarCodigoCancelacionMovimiento(Integer idMovimiento) {
        MovimientoCaja movimiento = movimientoCajaRepository.findById(idMovimiento)
                .orElseThrow(() -> new NotFoundException("Movimiento de caja no encontrado"));
        if (Boolean.TRUE.equals(movimiento.getCancelado())) throw new InvalidEntryException("El movimiento ya está cancelado");
        if (movimiento.getFechaSolicitudCancelacion() == null) throw new InvalidEntryException("El movimiento no tiene solicitud de cancelación");
        Persona admin = obtenerPersonaActual();
        String codigo = String.format("%04d", java.util.concurrent.ThreadLocalRandom.current().nextInt(10000));
        movimiento.setCodigoAutorizacion(codigo);
        movimiento.setFechaGeneracionCodigo(LocalDateTime.now());
        movimiento.setGeneroAutorizacion(admin.getUsuario());
        movimientoCajaRepository.save(movimiento);
        String detalle = movimiento.getMotivoCancelacion();
        if (movimiento.getAbono() != null) {
            detalle = "Abono #" + movimiento.getAbono().getIdAbono() + ", monto: $" + movimiento.getMonto()
                    + "; motivo: " + (detalle == null ? "Sin motivo" : detalle);
        }
        whatsAppService.notificarAutorizacion("Cancelación de movimiento de caja",
                "Movimiento #" + idMovimiento, detalle, codigo, admin.getUsuario());
        return new CodigoAutorizacionResponse(idMovimiento, codigo, movimiento.getFechaGeneracionCodigo(),
                movimiento.getFechaGeneracionCodigo().plusMinutes(10));
    }

    @Override
    @Transactional
    public void cancelarMovimiento(Integer idMovimiento, CancelarVentaRequest request) {
        MovimientoCaja movimiento = movimientoCajaRepository.findById(idMovimiento)
                .orElseThrow(() -> new NotFoundException("Movimiento de caja no encontrado"));
        if (Boolean.TRUE.equals(movimiento.getCancelado())) {
            throw new InvalidEntryException("El movimiento ya está cancelado");
        }

        Persona usuario = obtenerPersonaActual();
        boolean puedeAutorizar = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("CANCELACIONES_AUTORIZAR"));
        if (!puedeAutorizar) {
            if (movimiento.getFechaSolicitudCancelacion() == null) {
                throw new InvalidEntryException("Primero solicita la cancelación con un motivo");
            }
            if (request == null || request.codigo() == null || request.codigo().isBlank()) {
                throw new InvalidEntryException("Ingresa el código de autorización del administrador");
            }
            if (movimiento.getCodigoAutorizacion() == null || movimiento.getFechaGeneracionCodigo() == null
                    || movimiento.getFechaGeneracionCodigo().plusMinutes(10).isBefore(LocalDateTime.now())) {
                throw new InvalidEntryException("El código no existe o expiró");
            }
            if (!request.codigo().trim().equals(movimiento.getCodigoAutorizacion())) {
                throw new InvalidEntryException("Código de autorización inválido");
            }
        }
        Caja caja = movimiento.getCaja();
        Abono abono = movimiento.getAbono();
        if (abono != null) {
            if (Boolean.TRUE.equals(abono.getCancelado())) {
                throw new InvalidEntryException("El abono ya está cancelado");
            }
            Credito credito = abono.getCredito();
            credito.setSaldoInteres((credito.getSaldoInteres() == null ? 0 : credito.getSaldoInteres())
                    + (abono.getInteresPagado() == null ? 0 : abono.getInteresPagado()));
            credito.setSaldoCapital((credito.getSaldoCapital() == null ? 0 : credito.getSaldoCapital())
                    + (abono.getCapitalPagado() == null ? 0 : abono.getCapitalPagado()));
            credito.setSaldoPendiente(credito.getSaldoPendiente() + abono.getMonto());
            credito.setEstado(EstadoCredito.ACTIVO);
            creditoRepository.save(credito);

            Cliente cliente = credito.getCliente();
            cliente.setSaldoActual((cliente.getSaldoActual() == null ? 0 : cliente.getSaldoActual()) + abono.getMonto());
            clienteRepository.save(cliente);
            revertirPagoParcialidades(credito, abono.getInteresPagado(), abono.getCapitalPagado());

            abono.setCancelado(true);
            abono.setFechaCancelacion(LocalDateTime.now());
            abono.setUsuarioCancelacion(usuario);
            abonoRepository.save(abono);
            scoreCreditoService.recalcular(cliente);
        }

        double ajuste = movimiento.getTipo() == TipoMovimientoCaja.INGRESO
                ? -movimiento.getMonto() : movimiento.getMonto();
        caja.setSaldoActual(caja.getSaldoActual() + ajuste);
        cajaRepository.save(caja);
        movimiento.setCancelado(true);
        movimiento.setFechaCancelacion(LocalDateTime.now());
        movimiento.setUsuarioCancelacion(usuario);
        movimiento.setCodigoAutorizacion(null);
        movimiento.setFechaGeneracionCodigo(null);
        movimiento.setGeneroAutorizacion(null);
        movimientoCajaRepository.save(movimiento);
        auditoriaService.registrar("MovimientoCaja", idMovimiento, "ACTUALIZACION", usuario.getUsuario(),
                "Cancelación de movimiento de caja" + (abono == null ? "" : " y abono #" + abono.getIdAbono()));
    }

    private void revertirPagoParcialidades(Credito credito, Double interes, Double capital) {
        double interesRestante = interes == null ? 0 : interes;
        double capitalRestante = capital == null ? 0 : capital;
        List<ParcialidadCredito> parcialidades = parcialidadCreditoRepository
                .findByCreditoIdCreditoOrderByNumeroAsc(credito.getIdCredito());
        for (int i = parcialidades.size() - 1; i >= 0 && (interesRestante > 0.001 || capitalRestante > 0.001); i--) {
            ParcialidadCredito p = parcialidades.get(i);
            double quitarInteres = Math.min(interesRestante, p.getInteresPagado() == null ? 0 : p.getInteresPagado());
            double quitarCapital = Math.min(capitalRestante, p.getCapitalPagado() == null ? 0 : p.getCapitalPagado());
            p.setInteresPagado((p.getInteresPagado() == null ? 0 : p.getInteresPagado()) - quitarInteres);
            p.setCapitalPagado((p.getCapitalPagado() == null ? 0 : p.getCapitalPagado()) - quitarCapital);
            interesRestante -= quitarInteres;
            capitalRestante -= quitarCapital;
            if (p.getCapitalPagado() <= 0.001 && p.getInteresPagado() <= 0.001) p.setEstado("PENDIENTE");
            else if (p.getCapitalPagado() + 0.001 >= p.getCapitalProgramado()
                    && p.getInteresPagado() + 0.001 >= p.getInteresProgramado()) p.setEstado("PAGADA");
            else p.setEstado("PARCIAL");
            parcialidadCreditoRepository.save(p);
        }
    }

    @Override
    public CorteResponse previewCorte(Integer id) {
        Caja caja = buscarOExcepcion(id);
        LocalDateTime apertura = caja.getFechaApertura();
        LocalDateTime ahora = LocalDateTime.now();

        List<Venta> ventas = ventaRepository
                .findByCajaIdCajaAndFechaBetweenOrderByFechaDesc(id, apertura, ahora);
        List<MovimientoCaja> movs = movimientoCajaRepository
                .findByCajaIdCajaAndFechaBetweenOrderByFechaDesc(id, apertura, ahora);

        double totalVentas = ventas.stream()
                .filter(v -> v.getEstado() == EstadoVenta.COMPLETADA)
                .mapToDouble(Venta::getTotal).sum();
        double totalContado = ventas.stream()
                .filter(v -> v.getEstado() == EstadoVenta.COMPLETADA && v.getTipoVenta() == TipoVenta.CONTADO)
                .mapToDouble(Venta::getTotal).sum();
        double totalCredito = ventas.stream()
                .filter(v -> v.getEstado() == EstadoVenta.COMPLETADA && v.getTipoVenta() == TipoVenta.CREDITO)
                .mapToDouble(Venta::getTotal).sum();
        double totalIngresos = movs.stream()
                .filter(m -> !Boolean.TRUE.equals(m.getCancelado()) && m.getTipo() == TipoMovimientoCaja.INGRESO)
                .mapToDouble(MovimientoCaja::getMonto).sum();
        double totalEgresos = movs.stream()
                .filter(m -> !Boolean.TRUE.equals(m.getCancelado()) && m.getTipo() == TipoMovimientoCaja.EGRESO)
                .mapToDouble(MovimientoCaja::getMonto).sum();

        List<Gasto> gastosPeriodo = gastoRepository
                .findByCajaIdCajaAndFechaAutorizacionBetweenOrderByFechaAutorizacionAsc(id, apertura, ahora);
        double totalGastos = gastosPeriodo.stream()
                .mapToDouble(Gasto::getMonto).sum();

        double saldoInicial = caja.getSaldoActual() - totalIngresos + totalEgresos + totalGastos - totalContado;
        double saldoEsperado = saldoInicial + totalVentas + totalIngresos - totalEgresos - totalGastos;

        List<VentaPago> pagosEnRango = ventaPagoRepository.findByCajaAndFechaRange(id, apertura, ahora);
        List<Abono> abonosEnRango = abonoRepository
                .findByCajaIdCajaAndFechaBetweenOrderByFechaDesc(id, apertura, ahora);
        abonosEnRango = abonosEnRango.stream().filter(a -> !Boolean.TRUE.equals(a.getCancelado())).toList();
        double totalAbonos = abonosEnRango.stream().mapToDouble(Abono::getMonto).sum();

        java.util.Map<Integer, TipoPago> tipoPorId = pagosEnRango.stream()
                .map(VentaPago::getTipoPago)
                .collect(java.util.stream.Collectors.toMap(
                        TipoPago::getIdTipoPago, tp -> tp,
                        (a, b) -> a, java.util.LinkedHashMap::new));
        java.util.Map<Integer, Double> montosPorTipo = new java.util.HashMap<>(
                pagosEnRango.stream().collect(java.util.stream.Collectors.groupingBy(
                        vp -> vp.getTipoPago().getIdTipoPago(),
                        java.util.stream.Collectors.summingDouble(VentaPago::getMonto))));

        for (Abono a : abonosEnRango) {
            List<AbonoPago> pagosAbono = abonoPagoRepository.findByAbonoIdAbono(a.getIdAbono());
            if (!pagosAbono.isEmpty()) {
                for (AbonoPago ap : pagosAbono) {
                    Integer idTipo = ap.getTipoPago().getIdTipoPago();
                    montosPorTipo.merge(idTipo, ap.getMonto(), Double::sum);
                    tipoPorId.putIfAbsent(idTipo, ap.getTipoPago());
                }
            } else if (a.getTipoPago() != null) {
                Integer idTipo = a.getTipoPago().getIdTipoPago();
                montosPorTipo.merge(idTipo, a.getMonto(), Double::sum);
                tipoPorId.putIfAbsent(idTipo, a.getTipoPago());
            }
        }

        List<CorteDetallePagoDto> detallePagos = montosPorTipo.entrySet().stream()
                .map(e -> new CorteDetallePagoDto(
                        e.getKey(), tipoPorId.get(e.getKey()).getNombre(), e.getValue(), null))
                .toList();

        double totalReal = 0.0;
        double diferencia = 0.0;

        List<AbonoCorteDto> abonosList = abonosEnRango.stream()
                .map(a -> new AbonoCorteDto(
                        a.getIdAbono(),
                        a.getCredito() != null ? a.getCredito().getIdCredito() : null,
                        a.getCredito() != null ? a.getCredito().getFolio() : null,
                        a.getCredito() != null && a.getCredito().getCliente() != null
                                ? a.getCredito().getCliente().getIdCliente() : null,
                        a.getCredito() != null && a.getCredito().getCliente() != null
                                ? a.getCredito().getCliente().getNombre() + " "
                                  + a.getCredito().getCliente().getApellidoPaterno() : null,
                        a.getFecha(),
                        a.getTipoPago() != null ? a.getTipoPago().getNombre() : a.getTipo().name(),
                        a.getMonto(),
                        abonoPagoRepository.findByAbonoIdAbono(a.getIdAbono()).stream()
                                .map(ap -> new AbonoPagoResponse(
                                        ap.getIdAbonoPago(),
                                        ap.getTipoPago().getIdTipoPago(),
                                        ap.getTipoPago().getNombre(),
                                        ap.getMonto(),
                                        ap.getReferencia()))
                                .toList()))
                .toList();

        return new CorteResponse(null, id, caja.getNombre(),
                caja.getSucursal().getIdSucursal(), caja.getSucursal().getNombre(),
                saldoInicial,
                totalVentas, totalContado, totalCredito,
                totalIngresos, totalEgresos, totalGastos, totalAbonos, caja.getSaldoActual(),
                saldoEsperado,
                apertura, ahora, obtenerUsuarioActual(), detallePagos,
                gastosPeriodo.stream().map(this::toGastoResponse).toList(),
                totalReal, diferencia, abonosList);
    }

    @Override
    @Transactional
    public CorteResponse realizarCorte(Integer id) {
        Caja caja = buscarOExcepcion(id);
        validarCajaAbierta(caja);

        CorteResponse preview = previewCorte(id);
        String usuario = obtenerUsuarioActual();

        CorteCaja corte = CorteCaja.builder()
                .caja(caja)
                .saldoInicial(preview.saldoInicial())
                .totalVentas(preview.totalVentas())
                .totalVentasContado(preview.totalVentasContado())
                .totalVentasCredito(preview.totalVentasCredito())
                .totalIngresos(preview.totalIngresos())
                .totalEgresos(preview.totalEgresos())
                .totalAbonos(preview.totalAbonos())
                .saldoFinalContado(preview.saldoFinalContado())
                .fechaApertura(preview.fechaApertura())
                .fechaCierre(LocalDateTime.now())
                .usuario(obtenerPersonaActual())
                .build();
        corteCajaRepository.save(corte);

        caja.setEstado(CajaEstado.CERRADA);
        caja.setFechaCierre(LocalDateTime.now());
        cajaRepository.save(caja);

        if (preview.detallePagos() != null) {
            for (CorteDetallePagoDto dto : preview.detallePagos()) {
                TipoPago tp = tipoPagoRepository.findById(dto.idTipoPago())
                        .orElse(null);
                if (tp != null) {
                    CorteDetallePago det = CorteDetallePago.builder()
                            .corte(corte)
                            .tipoPago(tp)
                            .monto(dto.monto())
                            .build();
                    corteDetallePagoRepository.save(det);
                }
            }
        }

        auditoriaService.registrar("CorteCaja", corte.getIdCorte(), AccionAuditoria.CREACION.name(),
                usuario, "Corte realizado - Total ventas: $" + preview.totalVentas());

        return new CorteResponse(
                corte.getIdCorte(), id, caja.getNombre(),
                caja.getSucursal().getIdSucursal(), caja.getSucursal().getNombre(),
                preview.saldoInicial(), preview.totalVentas(),
                preview.totalVentasContado(), preview.totalVentasCredito(),
                preview.totalIngresos(), preview.totalEgresos(), preview.totalGastos(),
                preview.totalAbonos(),
                preview.saldoFinalContado(), preview.saldoEsperado(),
                preview.fechaApertura(),
                corte.getFechaCierre(), usuario, preview.detallePagos(),
                preview.gastos(),
                preview.totalReal(), preview.diferencia(),
                preview.abonos());
    }

    @Override
    @Transactional
    public CorteResponse actualizarDetallePagos(Integer idCorte,
                                                  List<CorteDetallePagoUpdateRequest.ItemDetallePago> pagos) {
        Persona persona = obtenerPersonaActual();

        CorteCaja corte = corteCajaRepository.findById(idCorte)
                .orElseThrow(() -> new NotFoundException("Corte no encontrado con id: " + idCorte));

        List<CorteDetallePago> detalles = corteDetallePagoRepository.findByCorteIdCorte(idCorte);

        for (CorteDetallePagoUpdateRequest.ItemDetallePago item : pagos) {
            detalles.stream()
                    .filter(d -> d.getTipoPago().getIdTipoPago().equals(item.idTipoPago()))
                    .findFirst()
                    .ifPresent(d -> d.setMontoReal(item.montoReal()));
        }
        corteDetallePagoRepository.saveAll(detalles);

        auditoriaService.registrar("CorteCaja", idCorte, AccionAuditoria.ACTUALIZACION.name(),
                persona.getUsuario(), "Conteos reales actualizados en corte #" + idCorte);

        return toResponseCorte(corte);
    }

    private CorteResponse toResponseCorte(CorteCaja corte) {
        List<CorteDetallePago> detalles = corteDetallePagoRepository.findByCorteIdCorte(corte.getIdCorte());
        List<CorteDetallePagoDto> detallePagos = detalles.stream()
                .map(d -> new CorteDetallePagoDto(
                        d.getTipoPago().getIdTipoPago(),
                        d.getTipoPago().getNombre(),
                        d.getMonto(),
                        d.getMontoReal()))
                .toList();
        double totalReal = detallePagos.stream()
                .filter(d -> d.montoReal() != null)
                .mapToDouble(CorteDetallePagoDto::montoReal)
                .sum();
        double sistema = detallePagos.stream().mapToDouble(CorteDetallePagoDto::monto).sum();
        double diferencia = totalReal > 0 ? totalReal - sistema : 0.0;

        List<AbonoCorteDto> abonosList = (corte.getCaja() != null && corte.getFechaApertura() != null
                && corte.getFechaCierre() != null)
                ? abonoRepository
                        .findByCajaIdCajaAndFechaBetweenOrderByFechaDesc(corte.getCaja().getIdCaja(),
                                corte.getFechaApertura(), corte.getFechaCierre())
                        .stream().map(this::toAbonoCorteDto).toList()
                : java.util.List.of();

        return new CorteResponse(
                corte.getIdCorte(), corte.getCaja().getIdCaja(), corte.getCaja().getNombre(),
                corte.getCaja().getSucursal().getIdSucursal(), corte.getCaja().getSucursal().getNombre(),
                corte.getSaldoInicial(), corte.getTotalVentas(),
                corte.getTotalVentasContado(), corte.getTotalVentasCredito(),
                corte.getTotalIngresos(), corte.getTotalEgresos(), 0.0,
                corte.getTotalAbonos() != null ? corte.getTotalAbonos() : 0.0,
                corte.getSaldoFinalContado(), null,
                corte.getFechaApertura(), corte.getFechaCierre(),
                corte.getUsuario().getUsuario(), detallePagos,
                java.util.List.of(),
                totalReal > 0 ? totalReal : null,
                totalReal > 0 ? diferencia : null,
                abonosList);
    }

    private AbonoCorteDto toAbonoCorteDto(Abono a) {
        String cliente = null;
        Integer idCredito = null;
        Integer idCliente = null;
        String folio = null;
        if (a.getCredito() != null) {
            idCredito = a.getCredito().getIdCredito();
            folio = a.getCredito().getFolio();
            if (a.getCredito().getCliente() != null) {
                idCliente = a.getCredito().getCliente().getIdCliente();
                cliente = a.getCredito().getCliente().getNombre() + " "
                        + a.getCredito().getCliente().getApellidoPaterno();
            }
        }
        return new AbonoCorteDto(
                a.getIdAbono(), idCredito, folio, idCliente, cliente,
                a.getFecha(),
                a.getTipoPago() != null ? a.getTipoPago().getNombre() : a.getTipo().name(),
                a.getMonto(),
                abonoPagoRepository.findByAbonoIdAbono(a.getIdAbono()).stream()
                        .map(ap -> new AbonoPagoResponse(
                                ap.getIdAbonoPago(),
                                ap.getTipoPago().getIdTipoPago(),
                                ap.getTipoPago().getNombre(),
                                ap.getMonto(),
                                ap.getReferencia()))
                        .toList());
    }

    private GastoResponse toGastoResponse(Gasto g) {
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

    private Caja buscarOExcepcion(Integer id) {
        return cajaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Caja no encontrada con id: " + id));
    }

    private void validarCajaAbierta(Caja caja) {
        if (caja.getEstado() != CajaEstado.ABIERTA) {
            throw new InvalidEntryException("La caja debe estar abierta para realizar esta operación");
        }
    }

    private void validarCajaUnica(Sucursal sucursal, TipoCaja tipo, Integer idExcluir) {
        // Una sucursal puede operar cualquier cantidad de cajas, incluso del mismo tipo.
    }

    private String obtenerUsuarioActual() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private Persona obtenerPersonaActual() {
        String username = obtenerUsuarioActual();
        return personaRepository.findByUsuario(username)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    private CajaResponse toResponse(Caja caja) {
        return new CajaResponse(
                caja.getIdCaja(), caja.getNombre(),
                caja.getTipo() != null ? caja.getTipo().name() : "NORMAL",
                caja.getSucursal().getIdSucursal(),
                caja.getSucursal().getNombre(),
                caja.getEstado().name(),
                caja.getSaldoActual(),
                caja.getFechaApertura(),
                caja.getFechaCierre(),
                caja.getActiva());
    }
}
