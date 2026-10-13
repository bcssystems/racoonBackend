package com.bcsystems.bonds.service.impl;

import com.bcsystems.bonds.domain.*;
import com.bcsystems.bonds.domain.en.*;
import com.bcsystems.bonds.dto.*;
import com.bcsystems.bonds.exception.InvalidEntryException;
import com.bcsystems.bonds.exception.NotFoundException;
import com.bcsystems.bonds.repository.*;
import com.bcsystems.bonds.service.AuditoriaService;
import com.bcsystems.bonds.service.VentaService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class VentaServiceImpl implements VentaService {

    private final VentaRepository ventaRepository;
    private final VentaDetalleRepository ventaDetalleRepository;
    private final CajaRepository cajaRepository;
    private final ClienteRepository clienteRepository;
    private final ProductoRepository productoRepository;
    private final InventarioSucursalRepository inventarioSucursalRepository;
    private final PersonaRepository personaRepository;
    private final AuditoriaService auditoriaService;
    private final VentaPagoRepository ventaPagoRepository;
    private final TipoPagoRepository tipoPagoRepository;
    private final CreditoRepository creditoRepository;
    private final ParcialidadCreditoRepository parcialidadCreditoRepository;
    private final MovimientoCreditoRepository movimientoCreditoRepository;
    private final ReservaProductoRepository reservaProductoRepository;
    private final CarritoItemRapidoRepository carritoItemRapidoRepository;
    private final MovimientoStockRepository movimientoStockRepository;
    private final ClienteIneRepository clienteIneRepository;

    @Override
    @Transactional
    public VentaResponse crear(VentaRequest request) {
        Caja caja = cajaRepository.findById(request.idCaja())
                .orElseThrow(() -> new NotFoundException("Caja no encontrada"));
        if (caja.getEstado() != CajaEstado.ABIERTA) {
            throw new InvalidEntryException("La caja debe estar abierta");
        }
        if (caja.getTipo() == null) {
            throw new InvalidEntryException("La caja chica no puede registrar ventas");
        }

        Persona usuario = obtenerPersonaActual();
        Cliente cliente = request.idCliente() != null
                ? clienteRepository.findById(request.idCliente()).orElse(null) : null;

        if (cliente != null && Boolean.TRUE.equals(cliente.getEnListaNegra())) {
            throw new InvalidEntryException("El cliente está en lista negra y no puede realizar compras a crédito");
        }

        Venta venta = Venta.builder()
                .caja(caja)
                .cliente(cliente)
                .usuario(usuario)
                .tipoVenta(TipoVenta.valueOf(request.tipoVenta()))
                .precioSeleccionado(request.precioSeleccionado())
                .subtotal(request.subtotal())
                .descuento(request.descuento())
                .total(request.total())
                .nota(request.nota())
                .folioPagare(generarFolioPagaré())
                .estado(EstadoVenta.COMPLETADA)
                .fecha(LocalDateTime.now())
                .build();
        venta = ventaRepository.save(venta);

        Sucursal sucursal = caja.getSucursal();

        List<VentaDetalle> detalles = new ArrayList<>();
        double subtotalEfectivo = 0.0;
        for (VentaDetalleRequest dto : request.detalles()) {
            VentaDetalle detalle = VentaDetalle.builder()
                    .venta(venta)
                    .producto(dto.idProducto() != null
                            ? productoRepository.findById(dto.idProducto()).orElse(null) : null)
                    .descripcion(dto.descripcion())
                    .tipoDetalle(dto.tipoDetalle() != null ? dto.tipoDetalle() : (dto.idItemRapido() != null ? "RAPIDO" : (dto.idProducto() == null ? "ENVIO" : "PRODUCTO")))
                    .idItemRapido(dto.idItemRapido())
                    .cantidad(dto.cantidad())
                     .precioUnitario(dto.precioUnitario())
                     .subtotal(dto.subtotal())
                     .costoUnitario(dto.costoUnitario() != null ? dto.costoUnitario()
                             : (dto.idProducto() != null && productoRepository.findById(dto.idProducto()).isPresent()
                             ? productoRepository.findById(dto.idProducto()).orElseThrow().getCostoPromedio() : null))
                     .atributosText(dto.atributosText())
                    .build();

            if (detalle.getProducto() != null && detalle.getProducto().getCostoPromedio() != null
                    && detalle.getPrecioUnitario() <= detalle.getProducto().getCostoPromedio()) {
                throw new InvalidEntryException("El precio de venta debe ser mayor al costo de " + detalle.getProducto().getNombre());
            }
            if (detalle.getProducto() != null && (detalle.getPrecioUnitario() == null || detalle.getPrecioUnitario() <= 0)) {
                throw new InvalidEntryException("El precio de venta debe estar configurado para " + detalle.getProducto().getNombre());
            }
            subtotalEfectivo += dto.subtotal();
            detalles.add(ventaDetalleRepository.save(detalle));

            if (dto.idProducto() != null && dto.idProducto() > 0) {
                Producto p = detalle.getProducto();
                ReservaProducto reserva = reservaProductoRepository
                        .findByCajaIdCajaAndIdProducto(request.idCaja(), dto.idProducto())
                        .orElseThrow(() -> new InvalidEntryException("El producto " + p.getNombre()
                                + " no est\u00e1 reservado para esta caja. Agr\u00e9galo nuevamente al carrito."));
                if (reserva.getExpiraEn().isBefore(LocalDateTime.now())) {
                    throw new InvalidEntryException("La reserva del producto " + p.getNombre()
                            + " ha expirado. Agr\u00e9galo nuevamente al carrito.");
                }
                if (reserva.getCantidad() < dto.cantidad()) {
                    throw new InvalidEntryException("La cantidad reservada de " + p.getNombre()
                            + " es insuficiente (reservado: " + reserva.getCantidad()
                            + ", solicitado: " + dto.cantidad() + ")");
                }
                InventarioSucursal inv = inventarioSucursalRepository
                        .findByProductoIdProductoAndSucursalIdSucursal(dto.idProducto(), sucursal.getIdSucursal())
                        .orElse(null);
                if (inv != null && inv.getStock() < dto.cantidad()) {
                    throw new InvalidEntryException("Stock insuficiente en " + sucursal.getNombre()
                            + " para: " + p.getNombre() + " (disponible: " + inv.getStock()
                            + ", solicitado: " + dto.cantidad() + ")");
                }
                p.setStockActual(p.getStockActual() - dto.cantidad());
                productoRepository.save(p);
                if (inv != null) {
                    inv.setStock(inv.getStock() - dto.cantidad());
                    inventarioSucursalRepository.save(inv);

                    movimientoStockRepository.save(MovimientoStock.builder()
                            .producto(p)
                            .sucursal(sucursal)
                            .tipoMovimiento(TipoMovimiento.SALIDA)
                            .cantidad(dto.cantidad())
                            .stockAnterior(inv.getStock() + dto.cantidad())
                            .stockNuevo(inv.getStock())
                            .referencia("Venta #" + venta.getIdVenta())
                            .usuario(usuario.getUsuario())
                            .observacion("Salida por venta")
                            .build());
                }
            }
        }

        if (request.pagos() != null && !request.pagos().isEmpty()) {
            double sumaPagos = request.pagos().stream().mapToDouble(VentaPagoRequest::monto).sum();
            double creditoSolicitado = request.montoCredito() != null ? request.montoCredito() : 0;
            double totalCubierto = sumaPagos + (TipoVenta.CREDITO.name().equals(request.tipoVenta()) ? creditoSolicitado : 0);
            if (totalCubierto + 0.001 < request.total()) {
                throw new InvalidEntryException("La suma de los pagos ($" + String.format("%.2f", sumaPagos)
                        + ") es menor al total de la venta ($" + String.format("%.2f", request.total()) + ")");
            }
            if (sumaPagos > request.total() + 0.001 && request.pagos().stream().anyMatch(p -> !esEfectivo(p.idTipoPago()))) {
                throw new InvalidEntryException("Solo se permite cambio cuando el excedente es efectivo");
            }
            for (VentaPagoRequest pagoReq : request.pagos()) {
                TipoPago tipoPago = tipoPagoRepository.findById(pagoReq.idTipoPago())
                        .orElseThrow(() -> new NotFoundException("Tipo de pago no encontrado"));
                boolean esCredito = tipoPago.getNombre().equalsIgnoreCase("credito")
                        || tipoPago.getNombre().equalsIgnoreCase("crédito");
                if (TipoVenta.CREDITO.name().equals(request.tipoVenta()) && esCredito) {
                    continue;
                }
                VentaPago pago = VentaPago.builder()
                        .venta(venta)
                        .tipoPago(tipoPago)
                        .monto(pagoReq.monto())
                        .referencia(pagoReq.referencia())
                        .build();
                ventaPagoRepository.save(pago);
            }
        }

        if (TipoVenta.CREDITO.name().equals(request.tipoVenta())) {
            if (cliente == null) {
                throw new InvalidEntryException("Se requiere un cliente para venta a credito");
            }
            if (cliente.getTieneCredito() == null || !cliente.getTieneCredito()) {
                throw new InvalidEntryException("El cliente no tiene credito habilitado");
            }
            double montoCredito = request.montoCredito() != null ? request.montoCredito() : request.total();
            double pagosNoCredito = 0;
            for (VentaPagoRequest pago : request.pagos() == null ? List.<VentaPagoRequest>of() : request.pagos()) {
                TipoPago tipo = tipoPagoRepository.findById(pago.idTipoPago()).orElseThrow();
                if (!tipo.getNombre().equalsIgnoreCase("credito") && !tipo.getNombre().equalsIgnoreCase("crédito")) {
                    pagosNoCredito += pago.monto();
                }
            }
            if (pagosNoCredito > request.total() + 0.001
                    && request.pagos().stream().anyMatch(p -> {
                TipoPago t = tipoPagoRepository.findById(p.idTipoPago()).orElseThrow();
                return !t.getNombre().equalsIgnoreCase("efectivo");
            })) {
                throw new InvalidEntryException("Solo se permite cambio con efectivo");
            }
            if (request.montoCredito() == null) {
                montoCredito = Math.max(0, request.total() - pagosNoCredito);
            }
            if (Math.abs((pagosNoCredito + montoCredito) - request.total()) > 0.01) {
                throw new InvalidEntryException("Los pagos y el monto financiado deben cubrir exactamente el total");
            }
            if (montoCredito < -0.001) {
                throw new InvalidEntryException("Los pagos exceden el total; solo se permite cambio en efectivo");
            }
            if (cliente.getLimiteCredito() != null && cliente.getLimiteCredito() > 0) {
                double disponible = cliente.getLimiteCredito()
                        - (cliente.getSaldoActual() != null ? cliente.getSaldoActual() : 0);
                if (montoCredito > disponible) {
                    throw new InvalidEntryException("El total ($" + String.format("%.2f", montoCredito)
                            + ") excede el limite de credito disponible ($" + String.format("%.2f", disponible) + ")");
                }
            }

            double porcentajeInteres = request.porcentajeInteres() != null ? request.porcentajeInteres() : 0;
            if (porcentajeInteres < 0) throw new InvalidEntryException("El interés no puede ser negativo");
            String periodicidad = normalizarPeriodicidad(request.periodicidad());
            int numeroParcialidades = request.numeroParcialidades() != null && request.numeroParcialidades() > 0
                    ? request.numeroParcialidades() : (request.plazoMeses() != null && request.plazoMeses() > 0 ? request.plazoMeses() : 1);
            double mesesEquivalentes = mesesEquivalentes(periodicidad, numeroParcialidades);
            double interesTotal = montoCredito * porcentajeInteres / 100 * mesesEquivalentes;
            double montoOriginal = montoCredito + interesTotal;
            int plazoMeses = (int) Math.ceil(mesesEquivalentes);
            LocalDate fechaPrimerPago = request.fechaPrimerPago() != null
                    ? request.fechaPrimerPago() : fechaInicialParcialidad(periodicidad);
            LocalDateTime fechaVencimiento = fechaParcialidad(fechaPrimerPago, periodicidad, numeroParcialidades - 1);
            Persona cobrador = request.idCobrador() != null
                    ? personaRepository.findById(request.idCobrador()).orElseThrow(() -> new NotFoundException("Cobrador no encontrado"))
                    : null;
            if (cobrador != null && !Boolean.TRUE.equals(cobrador.getActiva())) {
                throw new InvalidEntryException("El cobrador seleccionado está inactivo");
            }

            Credito credito = Credito.builder()
                    .venta(venta)
                    .folio(generarFolioPagaré())
                    .cliente(cliente)
                    .montoOriginal(montoOriginal)
                    .saldoPendiente(montoOriginal)
                    .plazoMeses(plazoMeses)
                    .porcentajeInteres(porcentajeInteres)
                    .capitalFinanciado(montoCredito)
                    .interesTotal(interesTotal)
                    .saldoCapital(montoCredito)
                    .saldoInteres(interesTotal)
                    .periodicidad(periodicidad)
                    .numeroParcialidades(numeroParcialidades)
                    .fechaPrimerPago(fechaPrimerPago.atStartOfDay())
                    .fechaVencimiento(fechaVencimiento)
                    .estado(EstadoCredito.ACTIVO)
                    .fechaCreacion(LocalDateTime.now())
                    .usuario(usuario)
                    .cobrador(cobrador)
                    .build();
            credito = creditoRepository.save(credito);
            crearParcialidades(credito, montoCredito, interesTotal, numeroParcialidades, periodicidad, fechaPrimerPago);

            MovimientoCredito mov = MovimientoCredito.builder()
                    .credito(credito)
                    .tipo(TipoMovimientoCredito.CARGO)
                    .monto(montoOriginal)
                    .interesAplicado(porcentajeInteres > 0 ? montoOriginal - montoCredito : 0.0)
                    .capitalAplicado(montoCredito)
                    .saldoAnterior(cliente.getSaldoActual() != null ? cliente.getSaldoActual() : 0)
                    .saldoNuevo((cliente.getSaldoActual() != null ? cliente.getSaldoActual() : 0) + montoOriginal)
                    .descripcion("Cargo por venta a credito #" + venta.getIdVenta())
                    .fecha(LocalDateTime.now())
                    .usuario(usuario)
                    .build();
            movimientoCreditoRepository.save(mov);

            cliente.setSaldoActual((cliente.getSaldoActual() != null ? cliente.getSaldoActual() : 0) + montoOriginal);
            clienteRepository.save(cliente);
            caja.setSaldoActual(caja.getSaldoActual() + (request.total() - montoCredito));
            cajaRepository.save(caja);
        } else {
            caja.setSaldoActual(caja.getSaldoActual() + request.total());
            cajaRepository.save(caja);
        }

        reservaProductoRepository.deleteByCajaIdCaja(request.idCaja());
        carritoItemRapidoRepository.deleteByCajaIdCajaNative(request.idCaja());

        auditoriaService.registrar("Venta", venta.getIdVenta(), AccionAuditoria.CREACION.name(),
                usuario.getUsuario(), "Venta $" + request.total() + " - " + caja.getNombre());

        return toResponse(venta, detalles);
    }

    @Override
    public VentaResponse obtenerPorId(Integer id) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Venta no encontrada"));
        List<VentaDetalle> detalles = ventaDetalleRepository.findByVentaIdVenta(id);
        return toResponse(venta, detalles);
    }

    @Override
    public List<VentaResponse> listarPorCaja(Integer idCaja) {
        return ventaRepository.findByCajaIdCajaAndEstadoOrderByFechaDesc(idCaja, EstadoVenta.COMPLETADA)
                .stream().map(v -> toResponse(v, ventaDetalleRepository.findByVentaIdVenta(v.getIdVenta())))
                .toList();
    }

    @Override
    public Page<VentaResponse> listar(Integer idSucursal, Integer idCaja, String estado, String tipoDetalle,
                                       LocalDateTime fechaInicio, LocalDateTime fechaFin, Pageable pageable) {
        EstadoVenta estadoEnum = estado != null ? EstadoVenta.valueOf(estado) : null;
        return ventaRepository.listarConFiltros(idSucursal, idCaja, estadoEnum, tipoDetalle, fechaInicio, fechaFin, pageable)
                .map(v -> toResponse(v, ventaDetalleRepository.findByVentaIdVenta(v.getIdVenta())));
    }

    @Override
    @Transactional
    public VentaResponse solicitarCancelacion(Integer id, String motivo) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Venta no encontrada"));
        if (venta.getEstado() != EstadoVenta.COMPLETADA
                && venta.getEstado() != EstadoVenta.COMPLETADA) {
            throw new InvalidEntryException("Solo se pueden solicitar cancelaciones de ventas completadas o en envío de pedido");
        }
        Persona solicitante = obtenerPersonaActual();
        if (motivo == null || motivo.isBlank()) {
            throw new InvalidEntryException("Debe indicar un motivo de cancelacion");
        }
        venta.setEstado(EstadoVenta.SOLICITADA_CANCELACION);
        venta.setMotivoCancelacion(motivo);
        venta.setSolicitanteCancelacion(solicitante);
        venta.setFechaSolicitudCancelacion(LocalDateTime.now());
        venta = ventaRepository.save(venta);

        auditoriaService.registrar("Venta", id, "ACTUALIZACION", solicitante.getUsuario(),
                "Solicitud de cancelacion: " + motivo);

        return toResponse(venta, ventaDetalleRepository.findByVentaIdVenta(id));
    }

    @Override
    @Transactional
    public CodigoAutorizacionResponse generarCodigo(Integer id) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Venta no encontrada"));
        if (venta.getEstado() != EstadoVenta.SOLICITADA_CANCELACION) {
            throw new InvalidEntryException("La venta no tiene una solicitud de cancelacion pendiente");
        }
        Persona admin = obtenerPersonaActual();
        String codigo = String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
        venta.setCodigoAutorizacion(codigo);
        venta.setFechaGeneracionCodigo(LocalDateTime.now());
        venta.setGeneroAutorizacion(admin.getUsuario());
        venta = ventaRepository.save(venta);

        auditoriaService.registrar("Venta", id, "ACTUALIZACION", admin.getUsuario(),
                "Codigo de autorizacion generado para la cancelacion de la Venta #" + id);

        LocalDateTime generadoEn = venta.getFechaGeneracionCodigo();
        return new CodigoAutorizacionResponse(id, codigo, generadoEn, generadoEn.plusMinutes(10));
    }

    @Override
    @Transactional
    public VentaResponse cancelar(Integer id, CancelarVentaRequest request) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Venta no encontrada"));

        Persona autorizador = obtenerPersonaActual();
        if (venta.getEstado() == EstadoVenta.CANCELADA) {
            throw new InvalidEntryException("La venta ya está cancelada");
        }
        if (venta.getEstado() != EstadoVenta.SOLICITADA_CANCELACION) {
            throw new InvalidEntryException("La venta no tiene una solicitud de cancelacion pendiente");
        }

        boolean tienePermiso = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("VENTAS_CANCELAR")
                        || a.getAuthority().equals("CANCELACIONES_AUTORIZAR"));

        if (!tienePermiso) {
            if (request == null || request.codigo() == null || request.codigo().isBlank()) {
                throw new InvalidEntryException("Ingresa el código de autorización del administrador");
            }
            if (venta.getFechaGeneracionCodigo() == null || venta.getCodigoAutorizacion() == null) {
                throw new InvalidEntryException("No hay código generado. Pídele al administrador generarlo");
            }
            if (!request.codigo().trim().equals(venta.getCodigoAutorizacion())) {
                throw new InvalidEntryException("Código de autorización inválido");
            }
            if (venta.getFechaGeneracionCodigo().plusMinutes(10).isBefore(LocalDateTime.now())) {
                venta.setCodigoAutorizacion(null);
                venta.setFechaGeneracionCodigo(null);
                venta.setGeneroAutorizacion(null);
                ventaRepository.save(venta);
                throw new InvalidEntryException("El código de autorización expiró. Pide uno nuevo al administrador");
            }
        }

        venta.setEstado(EstadoVenta.CANCELADA);
        venta.setAutorizadorCancelacion(autorizador);
        venta.setFechaAutorizacionCancelacion(LocalDateTime.now());
        venta.setCodigoAutorizacion(null);
        venta.setFechaGeneracionCodigo(null);
        venta.setGeneroAutorizacion(null);
        venta = ventaRepository.save(venta);

        Sucursal sucursal = venta.getCaja().getSucursal();
        List<VentaDetalle> detalles = ventaDetalleRepository.findByVentaIdVenta(id);
        for (VentaDetalle d : detalles) {
            if (d.getProducto() != null) {
                Producto p = d.getProducto();
                p.setStockActual(p.getStockActual() + d.getCantidad());
                productoRepository.save(p);
                InventarioSucursal inv = inventarioSucursalRepository
                        .findByProductoIdProductoAndSucursalIdSucursal(p.getIdProducto(), sucursal.getIdSucursal())
                        .orElse(null);
                if (inv != null) {
                    inv.setStock(inv.getStock() + d.getCantidad());
                    inventarioSucursalRepository.save(inv);

                    movimientoStockRepository.save(MovimientoStock.builder()
                            .producto(p)
                            .sucursal(sucursal)
                            .tipoMovimiento(TipoMovimiento.ENTRADA)
                            .cantidad(d.getCantidad())
                            .stockAnterior(inv.getStock() - d.getCantidad())
                            .stockNuevo(inv.getStock())
                            .referencia("Cancelacion Venta #" + id)
                            .usuario(autorizador.getUsuario())
                            .observacion("Devolucion por cancelacion de venta")
                            .build());
                }
            }
        }

        if (venta.getTipoVenta() == TipoVenta.CONTADO) {
            boolean tienePagos = !ventaPagoRepository.findByVentaIdVenta(id).isEmpty();
            if (tienePagos) {
                Caja caja = venta.getCaja();
                caja.setSaldoActual(caja.getSaldoActual() - venta.getTotal());
                cajaRepository.save(caja);
            }
        } else if (venta.getTipoVenta() == TipoVenta.CREDITO) {
            creditoRepository.findByVentaIdVenta(id).ifPresent(credito -> {
                if (credito.getEstado() == EstadoCredito.ACTIVO || credito.getEstado() == EstadoCredito.VENCIDO) {
                    Cliente cliente = credito.getCliente();
                    double abonado = credito.getMontoOriginal() - credito.getSaldoPendiente();
                    double reversar = abonado < credito.getMontoOriginal() ? abonado : credito.getMontoOriginal();
                    cliente.setSaldoActual(Math.max(0, (cliente.getSaldoActual() != null ? cliente.getSaldoActual() : 0) - reversar));
                    clienteRepository.save(cliente);
                    credito.setEstado(EstadoCredito.CANCELADO);
                    creditoRepository.save(credito);
                }
            });
        }

        auditoriaService.registrar("Venta", id, "ACTUALIZACION", autorizador.getUsuario(),
                "Cancelacion autorizada - Venta #" + id);

        return toResponse(venta, detalles);
    }

    @Override
    @Transactional
    public VentaResponse rechazarCancelacion(Integer id) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Venta no encontrada"));
        if (venta.getEstado() != EstadoVenta.SOLICITADA_CANCELACION) {
            throw new InvalidEntryException("La venta no tiene una solicitud de cancelacion pendiente");
        }
        Persona rechazador = obtenerPersonaActual();
        venta.setEstado(EstadoVenta.COMPLETADA);
        venta.setMotivoCancelacion(null);
        venta.setSolicitanteCancelacion(null);
        venta.setFechaSolicitudCancelacion(null);
        venta.setAutorizadorCancelacion(null);
        venta.setFechaAutorizacionCancelacion(null);
        venta = ventaRepository.save(venta);

        auditoriaService.registrar("Venta", id, "ACTUALIZACION", rechazador.getUsuario(),
                "Solicitud de cancelacion rechazada - Venta #" + id);

        return toResponse(venta, ventaDetalleRepository.findByVentaIdVenta(id));
    }

    @Override
    @Transactional
    public VentaResponse ponerEnEspera(Integer id) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Venta no encontrada"));
        if (venta.getEstado() != EstadoVenta.COMPLETADA) {
            throw new InvalidEntryException("Solo se puede poner en espera una venta completada");
        }
        if (venta.getTipoVenta() != TipoVenta.CONTADO) {
            throw new InvalidEntryException("Solo se pueden poner en espera ventas de contado");
        }
        // Una venta en espera no debe dejar un cobro aplicado. El inventario reservado se conserva.
        List<VentaPago> pagos = ventaPagoRepository.findByVentaIdVenta(id);
        if (!pagos.isEmpty()) {
            ventaPagoRepository.deleteAll(pagos);
        }
        Caja caja = venta.getCaja();
        caja.setSaldoActual(caja.getSaldoActual() - venta.getTotal());
        cajaRepository.save(caja);
        venta.setEstado(EstadoVenta.ESPERA);
        venta = ventaRepository.save(venta);
        return toResponse(venta, ventaDetalleRepository.findByVentaIdVenta(id));
    }

    @Override
    @Transactional
    public VentaResponse actualizarEspera(Integer id, VentaEsperaRequest request) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Venta no encontrada"));
        if (venta.getEstado() != EstadoVenta.ESPERA) {
            throw new InvalidEntryException("La venta no está en espera");
        }

        Persona usuario = obtenerPersonaActual();
        Sucursal sucursal = venta.getCaja().getSucursal();
        Cliente cliente = request.idCliente() != null
                ? clienteRepository.findById(request.idCliente()).orElse(null) : null;

        List<VentaDetalle> viejos = ventaDetalleRepository.findByVentaIdVenta(id);

        for (VentaDetalle d : viejos) {
            if (d.getProducto() != null) {
                restituirStock(d.getProducto(), d.getCantidad(), sucursal, usuario,
                        id, "Devolución por edición de venta en espera");
            }
        }
        ventaDetalleRepository.deleteAll(viejos);

        List<VentaDetalle> nuevos = new ArrayList<>();
        for (VentaDetalleRequest dto : request.detalles()) {
            VentaDetalle detalle = VentaDetalle.builder()
                    .venta(venta)
                    .producto(dto.idProducto() != null
                            ? productoRepository.findById(dto.idProducto()).orElse(null) : null)
                    .descripcion(dto.descripcion())
                    .cantidad(dto.cantidad())
                    .precioUnitario(dto.precioUnitario())
                    .subtotal(dto.subtotal())
                    .atributosText(dto.atributosText())
                    .build();
            if (detalle.getProducto() != null) {
                descontarStock(detalle.getProducto(), dto.cantidad(), sucursal, usuario, id);
            }
            nuevos.add(ventaDetalleRepository.save(detalle));
        }

        venta.setCliente(cliente);
        venta.setSubtotal(request.subtotal());
        venta.setDescuento(request.descuento());
        venta.setTotal(request.total());
        venta.setNota(request.nota());
        venta = ventaRepository.save(venta);

        return toResponse(venta, nuevos);
    }

    private void descontarStock(Producto p, Integer cantidad, Sucursal sucursal,
                                Persona usuario, Integer idVenta) {
        InventarioSucursal inv = inventarioSucursalRepository
                .findByProductoIdProductoAndSucursalIdSucursal(p.getIdProducto(), sucursal.getIdSucursal())
                .orElse(null);
        if (inv != null && inv.getStock() < cantidad) {
            throw new InvalidEntryException("Stock insuficiente en " + sucursal.getNombre()
                    + " para: " + p.getNombre() + " (disponible: " + inv.getStock()
                    + ", solicitado: " + cantidad + ")");
        }
        p.setStockActual(p.getStockActual() - cantidad);
        productoRepository.save(p);
        if (inv != null) {
            inv.setStock(inv.getStock() - cantidad);
            inventarioSucursalRepository.save(inv);

            movimientoStockRepository.save(MovimientoStock.builder()
                    .producto(p)
                    .sucursal(sucursal)
                    .tipoMovimiento(TipoMovimiento.SALIDA)
                    .cantidad(cantidad)
                    .stockAnterior(inv.getStock() + cantidad)
                    .stockNuevo(inv.getStock())
                    .referencia("Venta en Espera #" + idVenta)
                    .usuario(usuario.getUsuario())
                    .observacion("Salida por venta en espera")
                    .build());
        }
    }

    private void restituirStock(Producto p, Integer cantidad, Sucursal sucursal,
                                Persona usuario, Integer idVenta, String observacion) {
        p.setStockActual(p.getStockActual() + cantidad);
        productoRepository.save(p);
        InventarioSucursal inv = inventarioSucursalRepository
                .findByProductoIdProductoAndSucursalIdSucursal(p.getIdProducto(), sucursal.getIdSucursal())
                .orElse(null);
        if (inv != null) {
            inv.setStock(inv.getStock() + cantidad);
            inventarioSucursalRepository.save(inv);

            movimientoStockRepository.save(MovimientoStock.builder()
                    .producto(p)
                    .sucursal(sucursal)
                    .tipoMovimiento(TipoMovimiento.ENTRADA)
                    .cantidad(cantidad)
                    .stockAnterior(inv.getStock() - cantidad)
                    .stockNuevo(inv.getStock())
                    .referencia("Venta en Espera #" + idVenta)
                    .usuario(usuario.getUsuario())
                    .observacion(observacion)
                    .build());
        }
    }

    @Override
    @Transactional
    public VentaResponse reanudar(Integer id) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Venta no encontrada"));
        if (venta.getEstado() != EstadoVenta.ESPERA) {
            throw new InvalidEntryException("La venta no está en espera");
        }
        venta.setEstado(EstadoVenta.COMPLETADA);
        venta = ventaRepository.save(venta);
        return toResponse(venta, ventaDetalleRepository.findByVentaIdVenta(id));
    }

    @Override
    @Transactional
    public VentaResponse cancelarEspera(Integer id) {
        Venta venta = ventaRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Venta no encontrada"));
        if (venta.getEstado() != EstadoVenta.ESPERA) {
            throw new InvalidEntryException("La venta no está en espera");
        }
        Persona usuario = obtenerPersonaActual();
        venta.setEstado(EstadoVenta.CANCELADA);
        venta = ventaRepository.save(venta);

        Sucursal sucursal = venta.getCaja().getSucursal();
        List<VentaDetalle> detalles = ventaDetalleRepository.findByVentaIdVenta(id);
        for (VentaDetalle d : detalles) {
            if (d.getProducto() != null) {
                Producto p = d.getProducto();
                p.setStockActual(p.getStockActual() + d.getCantidad());
                productoRepository.save(p);
                InventarioSucursal inv = inventarioSucursalRepository
                        .findByProductoIdProductoAndSucursalIdSucursal(p.getIdProducto(), sucursal.getIdSucursal())
                        .orElse(null);
                if (inv != null) {
                    inv.setStock(inv.getStock() + d.getCantidad());
                    inventarioSucursalRepository.save(inv);

                    movimientoStockRepository.save(MovimientoStock.builder()
                            .producto(p)
                            .sucursal(sucursal)
                            .tipoMovimiento(TipoMovimiento.ENTRADA)
                            .cantidad(d.getCantidad())
                            .stockAnterior(inv.getStock() - d.getCantidad())
                            .stockNuevo(inv.getStock())
                            .referencia("Recuperacion Venta en Espera #" + id)
                            .usuario(usuario.getUsuario())
                            .observacion("Devolucion por recuperacion de venta en espera")
                            .build());
                }
            }
        }

        auditoriaService.registrar("Venta", id, "ACTUALIZACION", usuario.getUsuario(),
                "Recuperacion de venta en espera - Venta #" + id);

        return toResponse(venta, detalles);
    }

    @Override
    public List<VentaResponse> ventasEnEspera(Integer idCaja) {
        return ventaRepository.findByCajaIdCajaAndEstadoOrderByFechaDesc(idCaja, EstadoVenta.ESPERA)
                .stream().map(v -> toResponse(v, ventaDetalleRepository.findByVentaIdVenta(v.getIdVenta())))
                .toList();
    }

    @Override
    public List<VentaResponse> listarPorSucursal(Integer idSucursal) {
        return ventaRepository.findByCajaSucursalIdSucursalAndEstadoInOrderByFechaDesc(
                        idSucursal,
                        List.of(EstadoVenta.COMPLETADA, EstadoVenta.CANCELADA))
                .stream().limit(50)
                .map(v -> toResponse(v, ventaDetalleRepository.findByVentaIdVenta(v.getIdVenta())))
                .toList();
    }

    @Override
    @Transactional
    public VentaResponse registrarPago(Integer id, List<VentaPagoRequest> pagos) {
        Venta venta = ventaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Venta no encontrada"));
        if (venta.getEstado() != EstadoVenta.ESPERA) {
            throw new InvalidEntryException("La venta no está pendiente de cobro");
        }
        if (venta.getTipoVenta() != TipoVenta.CONTADO) {
            throw new InvalidEntryException("Una venta en envío de pedido solo puede ser de contado");
        }
        if (pagos == null || pagos.isEmpty()) {
            throw new InvalidEntryException("Debe indicar al menos una forma de pago");
        }
        if (!ventaPagoRepository.findByVentaIdVenta(id).isEmpty()) {
            throw new InvalidEntryException("La venta ya tiene pagos registrados");
        }
        double sumaPagos = pagos.stream().mapToDouble(VentaPagoRequest::monto).sum();
        if (sumaPagos + 0.01 < venta.getTotal()) {
            throw new InvalidEntryException("La suma de los pagos ($" + String.format("%.2f", sumaPagos)
                    + ") es menor al total de la venta ($" + String.format("%.2f", venta.getTotal()) + ")");
        }
        if (sumaPagos > venta.getTotal() + 0.001 && pagos.stream().anyMatch(p -> !esEfectivo(p.idTipoPago()))) {
            throw new InvalidEntryException("Solo se permite cambio cuando el excedente es efectivo");
        }
        for (VentaPagoRequest pagoReq : pagos) {
            TipoPago tipoPago = tipoPagoRepository.findById(pagoReq.idTipoPago())
                    .orElseThrow(() -> new NotFoundException("Tipo de pago no encontrado"));
            VentaPago pago = VentaPago.builder()
                    .venta(venta)
                    .tipoPago(tipoPago)
                    .monto(pagoReq.monto())
                    .referencia(pagoReq.referencia())
                    .build();
            ventaPagoRepository.save(pago);
        }
        venta.setEstado(EstadoVenta.COMPLETADA);
        ventaRepository.save(venta);

        Persona usuario = obtenerPersonaActual();
        Caja caja = venta.getCaja();
        caja.setSaldoActual(caja.getSaldoActual() + venta.getTotal());
        cajaRepository.save(caja);

        auditoriaService.registrar("Venta", id, AccionAuditoria.ACTUALIZACION.name(),
                usuario.getUsuario(), "Pago registrado - Venta #" + id);

        return toResponse(venta, ventaDetalleRepository.findByVentaIdVenta(id));
    }

    @Override
    @Transactional
    public VentaResponse ventaRapida(Integer idCaja, String descripcion, Double precioCompra,
                                      Double precioVenta, Integer cantidad, Integer idCliente) {
        Caja caja = cajaRepository.findById(idCaja)
                .orElseThrow(() -> new NotFoundException("Caja no encontrada"));
        if (caja.getEstado() != CajaEstado.ABIERTA) {
            throw new InvalidEntryException("La caja debe estar abierta");
        }
        if (caja.getTipo() == null) {
            throw new InvalidEntryException("La caja chica no puede registrar ventas");
        }

        Persona usuario = obtenerPersonaActual();
        Cliente cliente = idCliente != null
                ? clienteRepository.findById(idCliente).orElse(null) : null;

        Double subtotal = precioVenta * cantidad;

        Venta venta = Venta.builder()
                .caja(caja)
                .cliente(cliente)
                .usuario(usuario)
                .tipoVenta(TipoVenta.CONTADO)
                .precioSeleccionado(1)
                .subtotal(subtotal)
                .descuento(0.0)
                .total(subtotal)
                .estado(EstadoVenta.COMPLETADA)
                .fecha(LocalDateTime.now())
                .build();
        venta = ventaRepository.save(venta);

        VentaDetalle detalle = VentaDetalle.builder()
                .venta(venta)
                .descripcion(descripcion)
                .cantidad(cantidad)
                .precioUnitario(precioVenta)
                .subtotal(subtotal)
                .costoUnitario(precioCompra)
                .build();
        ventaDetalleRepository.save(detalle);

        caja.setSaldoActual(caja.getSaldoActual() + subtotal);
        cajaRepository.save(caja);

        return toResponse(venta, List.of(detalle));
    }

    private Persona obtenerPersonaActual() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return personaRepository.findByUsuario(username)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    private String normalizarPeriodicidad(String periodicidad) {
        if (periodicidad == null || periodicidad.isBlank()) return "MENSUAL";
        String value = periodicidad.trim().toUpperCase();
        if (!List.of("MENSUAL", "QUINCENAL", "SEMANAL").contains(value)) {
            throw new InvalidEntryException("La periodicidad debe ser MENSUAL, QUINCENAL o SEMANAL");
        }
        return value;
    }

    private double mesesEquivalentes(String periodicidad, int parcialidades) {
        return switch (periodicidad) {
            case "QUINCENAL" -> parcialidades / 2.0;
            case "SEMANAL" -> parcialidades / 4.0;
            default -> parcialidades;
        };
    }

    private LocalDate fechaInicialParcialidad(String periodicidad) {
        LocalDate hoy = LocalDate.now();
        return switch (periodicidad) {
            case "QUINCENAL" -> hoy.plusDays(15);
            case "SEMANAL" -> hoy.plusDays(7);
            default -> hoy.plusMonths(1);
        };
    }

    private LocalDateTime fechaParcialidad(LocalDate primera, String periodicidad, int indice) {
        long dias = switch (periodicidad) {
            case "QUINCENAL" -> 15L;
            case "SEMANAL" -> 7L;
            default -> 0L;
        };
        return dias > 0 ? primera.plusDays(dias * indice).atStartOfDay() : primera.plusMonths(indice).atStartOfDay();
    }

    private void crearParcialidades(Credito credito, double capital, double interes, int cantidad,
                                    String periodicidad, LocalDate primera) {
        double capitalBase = Math.floor((capital / cantidad) * 100.0) / 100.0;
        double interesBase = Math.floor((interes / cantidad) * 100.0) / 100.0;
        for (int i = 1; i <= cantidad; i++) {
            double capitalParcial = i == cantidad ? capital - capitalBase * (cantidad - 1) : capitalBase;
            double interesParcial = i == cantidad ? interes - interesBase * (cantidad - 1) : interesBase;
            parcialidadCreditoRepository.save(ParcialidadCredito.builder()
                    .credito(credito)
                    .numero(i)
                    .fechaVencimiento(fechaParcialidad(primera, periodicidad, i - 1))
                    .capitalProgramado(redondear(capitalParcial))
                    .interesProgramado(redondear(interesParcial))
                    .build());
        }
    }

    private double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }

    private String generarFolioPagaré() {
        String prefix = "CR-";
        int num = 1;
        while (creditoRepository.existsByFolio(prefix + String.format("%05d", num))
                || ventaRepository.existsByFolioPagare(prefix + String.format("%05d", num))) {
            num++;
        }
        return prefix + String.format("%05d", num);
    }

    private boolean esEfectivo(Integer idTipoPago) {
        TipoPago tipo = tipoPagoRepository.findById(idTipoPago)
                .orElseThrow(() -> new NotFoundException("Tipo de pago no encontrado"));
        return tipo.getNombre().equalsIgnoreCase("efectivo");
    }

    private VentaResponse toResponse(Venta v, List<VentaDetalle> detalles) {
        List<VentaDetalleResponse> detalleResponses = detalles.stream()
                .map(d -> new VentaDetalleResponse(
                        d.getIdVentaDetalle(),
                        d.getProducto() != null ? d.getProducto().getIdProducto() : null,
                        d.getProducto() != null ? d.getProducto().getSku() : null,
                        d.getProducto() != null ? d.getProducto().getNombre() : null,
                        d.getDescripcion(),
                        d.getTipoDetalle(),
                        d.getIdItemRapido(),
                        d.getCantidad(),
                        d.getProducto() != null ? d.getProducto().getUnidadMedida() : null,
                         d.getPrecioUnitario(), d.getSubtotal(), d.getCostoUnitario(),
                        d.getAtributosText()))
                .toList();

        List<VentaPagoResponse> pagoResponses = ventaPagoRepository.findByVentaIdVenta(v.getIdVenta())
                .stream().map(p -> new VentaPagoResponse(
                        p.getIdVentaPago(),
                        p.getTipoPago().getIdTipoPago(),
                        p.getTipoPago().getNombre(),
                        p.getMonto(),
                        p.getReferencia()))
                .toList();

        Credito credito = creditoRepository.findByVentaIdVenta(v.getIdVenta()).orElse(null);

        return new VentaResponse(
                v.getIdVenta(), v.getCaja().getIdCaja(),
                v.getCaja().getNombre(),
                v.getCaja().getSucursal().getIdSucursal(),
                v.getCaja().getSucursal().getNombre(),
                v.getCliente() != null ? v.getCliente().getIdCliente() : null,
                v.getCliente() != null ? v.getCliente().getNombre() + " " + v.getCliente().getApellidoPaterno() : null,
                v.getUsuario().getUsuario(),
                v.getTipoVenta().name(), v.getPrecioSeleccionado(),
                v.getSubtotal(), v.getDescuento(), v.getTotal(),
                v.getEstado().name(), v.getNota(), v.getFecha(), detalleResponses, pagoResponses,
                credito != null && v.getFolioPagare() == null ? credito.getFolio() : v.getFolioPagare(),
                credito != null ? credito.getPlazoMeses() : null,
                credito != null ? credito.getPorcentajeInteres() : null,
                credito != null ? credito.getPeriodicidad() : null,
                credito != null ? credito.getNumeroParcialidades() : null,
                credito != null ? credito.getFechaPrimerPago() : null,
                credito != null ? credito.getCapitalFinanciado() : null,
                credito != null ? credito.getInteresTotal() : null,
                credito != null ? credito.getSaldoCapital() : null,
                credito != null ? credito.getSaldoInteres() : null,
                v.getMotivoCancelacion(),
                v.getSolicitanteCancelacion() != null ? v.getSolicitanteCancelacion().getUsuario() : null,
                v.getFechaSolicitudCancelacion(),
                v.getAutorizadorCancelacion() != null ? v.getAutorizadorCancelacion().getUsuario() : null,
                v.getFechaAutorizacionCancelacion(),
                v.getFechaGeneracionCodigo() != null);
    }
}
