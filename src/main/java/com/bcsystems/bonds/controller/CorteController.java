package com.bcsystems.bonds.controller;

import com.bcsystems.bonds.domain.CorteCaja;
import com.bcsystems.bonds.dto.AbonoCorteDto;
import com.bcsystems.bonds.dto.AbonoPagoResponse;
import com.bcsystems.bonds.dto.CorteDetallePagoDto;
import com.bcsystems.bonds.dto.CorteDetallePagoUpdateRequest;
import com.bcsystems.bonds.dto.CorteResponse;
import com.bcsystems.bonds.repository.AbonoPagoRepository;
import com.bcsystems.bonds.repository.AbonoRepository;
import com.bcsystems.bonds.repository.CorteCajaRepository;
import com.bcsystems.bonds.repository.CorteDetallePagoRepository;
import com.bcsystems.bonds.repository.MovimientoCajaRepository;
import com.bcsystems.bonds.repository.PersonaRepository;
import com.bcsystems.bonds.service.CajaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;
import com.bcsystems.bonds.exception.NotFoundException;
import com.bcsystems.bonds.exception.InvalidEntryException;
import com.bcsystems.bonds.domain.Persona;
import com.bcsystems.bonds.domain.MovimientoCaja;
import com.bcsystems.bonds.service.AuditoriaService;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cortes")
public class CorteController {

    private final CorteCajaRepository corteCajaRepository;
    private final CorteDetallePagoRepository corteDetallePagoRepository;
    private final AbonoRepository abonoRepository;
    private final AbonoPagoRepository abonoPagoRepository;
    private final CajaService cajaService;
    private final MovimientoCajaRepository movimientoCajaRepository;
    private final PersonaRepository personaRepository;
    private final AuditoriaService auditoriaService;

    public CorteController(CorteCajaRepository corteCajaRepository,
                           CorteDetallePagoRepository corteDetallePagoRepository,
                           AbonoRepository abonoRepository,
                           AbonoPagoRepository abonoPagoRepository,
                           CajaService cajaService,
                           MovimientoCajaRepository movimientoCajaRepository,
                           PersonaRepository personaRepository,
                           AuditoriaService auditoriaService) {
        this.corteCajaRepository = corteCajaRepository;
        this.corteDetallePagoRepository = corteDetallePagoRepository;
        this.abonoRepository = abonoRepository;
        this.abonoPagoRepository = abonoPagoRepository;
        this.cajaService = cajaService;
        this.movimientoCajaRepository = movimientoCajaRepository;
        this.personaRepository = personaRepository;
        this.auditoriaService = auditoriaService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CORTES_VER')")
    public ResponseEntity<Page<CorteResponse>> listar(
            @RequestParam(required = false) Integer idSucursal,
            @RequestParam(required = false) Integer idCaja,
            @RequestParam(required = false) LocalDateTime fechaInicio,
            @RequestParam(required = false) LocalDateTime fechaFin,
            @PageableDefault(size = 20, sort = "fechaCierre", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<CorteCaja> page = corteCajaRepository.listarConFiltros(idSucursal, idCaja, fechaInicio, fechaFin, pageable);
        return ResponseEntity.ok(page.map(this::toResponse));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CORTES_VER')")
    public ResponseEntity<CorteResponse> obtenerPorId(@PathVariable Integer id) {
        CorteCaja corte = corteCajaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Corte no encontrado con id: " + id));
        return ResponseEntity.ok(toResponse(corte));
    }

    @GetMapping("/{id}/movimientos")
    @PreAuthorize("hasAuthority('CORTES_MOVIMIENTOS_VER')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<com.bcsystems.bonds.dto.CorteMovimientoDto>> movimientos(@PathVariable Integer id) {
        CorteCaja corte = corteCajaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Corte no encontrado"));
        List<com.bcsystems.bonds.dto.CorteMovimientoDto> movimientos = movimientoCajaRepository
                .findByCajaIdCajaAndFechaBetweenOrderByFechaDesc(corte.getCaja().getIdCaja(),
                        corte.getFechaApertura(), corte.getFechaCierre())
                .stream().map(this::toMovimientoCorte).toList();
        return ResponseEntity.ok(movimientos);
    }

    private com.bcsystems.bonds.dto.CorteMovimientoDto toMovimientoCorte(MovimientoCaja movimiento) {
        String tipo = movimiento.getVenta() != null ? "VENTA"
                : movimiento.getAbono() != null ? "ABONO"
                : movimiento.getTipo().name();
        String cliente = null;
        String folioCredito = null;
        if (movimiento.getAbono() != null && movimiento.getAbono().getCredito() != null) {
            var credito = movimiento.getAbono().getCredito();
            folioCredito = credito.getFolio();
            if (credito.getCliente() != null) cliente = nombreCliente(credito.getCliente());
        } else if (movimiento.getVenta() != null && movimiento.getVenta().getCliente() != null) {
            cliente = nombreCliente(movimiento.getVenta().getCliente());
        }
        return new com.bcsystems.bonds.dto.CorteMovimientoDto(
                movimiento.getIdMovimientoCaja(), movimiento.getFecha(), tipo, movimiento.getMotivo(),
                movimiento.getMonto(), movimiento.getUsuario() != null ? movimiento.getUsuario().getUsuario() : null,
                cliente, folioCredito,
                movimiento.getVenta() != null ? movimiento.getVenta().getIdVenta() : null,
                movimiento.getCancelado(), movimiento.getMotivoCancelacion());
    }

    private String nombreCliente(com.bcsystems.bonds.domain.Cliente cliente) {
        return (cliente.getNombre() + " " + cliente.getApellidoPaterno()).trim();
    }

    @PostMapping("/{id}/recibir")
    @PreAuthorize("hasAuthority('CORTES_RECIBIR')")
    @Transactional
    public ResponseEntity<CorteResponse> recibir(@PathVariable Integer id) {
        CorteCaja corte = corteCajaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Corte no encontrado"));
        if (corte.getRecibidoPor() != null) throw new InvalidEntryException("El corte ya fue recibido");
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Persona recibidor = personaRepository.findByUsuario(username)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        corte.setRecibidoPor(recibidor);
        corte.setFechaRecepcion(LocalDateTime.now());
        corte = corteCajaRepository.save(corte);
        auditoriaService.registrar("CorteCaja", id, "ACTUALIZACION", recibidor.getUsuario(), "Corte recibido por " + recibidor.getUsuario());
        return ResponseEntity.ok(toResponse(corte));
    }

    @PutMapping("/{id}/detalle-pagos")
    @PreAuthorize("hasAuthority('CORTES_EDITAR')")
    public ResponseEntity<CorteResponse> actualizarDetallePagos(
            @PathVariable Integer id,
            @RequestBody CorteDetallePagoUpdateRequest request) {
        return ResponseEntity.ok(cajaService.actualizarDetallePagos(id, request.pagos()));
    }

    private CorteResponse toResponse(CorteCaja c) {
        List<CorteDetallePagoDto> detallePagos = corteDetallePagoRepository.findByCorteIdCorte(c.getIdCorte())
                .stream().map(d -> new CorteDetallePagoDto(
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

        List<AbonoCorteDto> abonosList = (c.getCaja() != null && c.getFechaApertura() != null
                && c.getFechaCierre() != null)
                ? abonoRepository
                        .findByCajaIdCajaAndFechaBetweenOrderByFechaDesc(
                                c.getCaja().getIdCaja(),
                                c.getFechaApertura(), c.getFechaCierre())
                        .stream().filter(a -> !Boolean.TRUE.equals(a.getCancelado())).map(a -> new AbonoCorteDto(
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
                        .toList()
                : java.util.List.of();

        return new CorteResponse(
                c.getIdCorte(), c.getCaja().getIdCaja(), c.getCaja().getNombre(),
                c.getCaja().getSucursal().getIdSucursal(), c.getCaja().getSucursal().getNombre(),
                c.getSaldoInicial(), c.getTotalVentas(),
                c.getTotalVentasContado(), c.getTotalVentasCredito(),
                c.getTotalIngresos(), c.getTotalEgresos(), 0.0,
                c.getTotalAbonos() != null ? c.getTotalAbonos() : 0.0,
                c.getSaldoFinalContado(), null,
                c.getFechaApertura(), c.getFechaCierre(),
                c.getUsuario().getUsuario(), detallePagos,
                java.util.List.of(),
                totalReal > 0 ? totalReal : null,
                totalReal > 0 ? diferencia : null,
                 abonosList,
                 c.getRecibidoPor() == null ? null : c.getRecibidoPor().getNombre() + " " + c.getRecibidoPor().getApellido(),
                 c.getFechaRecepcion());
    }
}
