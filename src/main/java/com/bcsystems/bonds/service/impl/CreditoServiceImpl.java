package com.bcsystems.bonds.service.impl;

import com.bcsystems.bonds.domain.*;
import com.bcsystems.bonds.domain.en.*;
import com.bcsystems.bonds.dto.*;
import com.bcsystems.bonds.exception.InvalidEntryException;
import com.bcsystems.bonds.exception.NotFoundException;
import com.bcsystems.bonds.repository.*;
import com.bcsystems.bonds.service.CreditoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class CreditoServiceImpl implements CreditoService {

    private final CreditoRepository creditoRepository;
    private final MovimientoCreditoRepository movimientoCreditoRepository;
    private final ParcialidadCreditoRepository parcialidadCreditoRepository;
    private final AbonoRepository abonoRepository;
    private final AbonoPagoRepository abonoPagoRepository;
    private final ClienteRepository clienteRepository;
    private final PersonaRepository personaRepository;
    private final TipoPagoRepository tipoPagoRepository;
    private final com.bcsystems.bonds.service.ConfiguracionService configuracionService;
    private final ClienteIneRepository clienteIneRepository;
    private final CajaRepository cajaRepository;
    private final MovimientoCajaRepository movimientoCajaRepository;

    @Override
    public List<CreditoResponse> listarCreditosPorCliente(Integer idCliente) {
        return creditoRepository.findByClienteIdClienteOrderByFechaCreacionDesc(idCliente)
                .stream().map(this::toCreditoResponse).toList();
    }

    @Override
    public List<MovimientoCreditoResponse> listarMovimientosPorCliente(Integer idCliente) {
        return movimientoCreditoRepository.findByCreditoClienteIdClienteOrderByFechaDesc(idCliente)
                .stream().map(this::toMovimientoResponse).toList();
    }

    @Override
    @Transactional
    public AbonoResponse registrarAbono(AbonoRequest request) {
        Credito credito = creditoRepository.findById(request.idCredito())
                .orElseThrow(() -> new NotFoundException("Credito no encontrado"));

        if (credito.getEstado() != EstadoCredito.ACTIVO) {
            throw new InvalidEntryException("El credito no esta activo");
        }

        if (request.monto() <= 0) {
            throw new InvalidEntryException("El monto debe ser mayor a cero");
        }

        if (request.monto() > credito.getSaldoPendiente()) {
            throw new InvalidEntryException("El monto excede el saldo pendiente ($" + String.format("%.2f", credito.getSaldoPendiente()) + ")");
        }

        Persona usuario = obtenerPersonaActual();
        TipoAbono tipo = "LIQUIDACION".equals(request.tipo()) ? TipoAbono.LIQUIDACION : TipoAbono.PARCIAL;
        List<TipoPago> tiposPago = resolverTiposPago(request.pagos(), request.idTipoPago());
        Caja caja = resolverCaja(request.idCaja());

        prepararSaldosCredito(credito);
        double saldoAnterior = credito.getSaldoPendiente();
        if (tipo == TipoAbono.LIQUIDACION && request.monto() + 0.01 < saldoAnterior) {
            throw new InvalidEntryException("La liquidación debe cubrir el saldo total del crédito");
        }
        double interesPagado = Math.min(request.monto(), credito.getSaldoInteres());
        double capitalPagado = request.monto() - interesPagado;
        double saldoInteresNuevo = credito.getSaldoInteres() - interesPagado;
        double saldoCapitalNuevo = credito.getSaldoCapital() - capitalPagado;
        double saldoNuevo = saldoInteresNuevo + saldoCapitalNuevo;
        if (saldoNuevo <= 0.01) tipo = TipoAbono.LIQUIDACION;

        Abono abono = Abono.builder()
                .credito(credito)
                .monto(request.monto())
                .interesPagado(interesPagado)
                .capitalPagado(capitalPagado)
                .tipo(tipo)
                .fecha(LocalDateTime.now())
                .usuario(usuario)
                .tipoPago(tiposPago.isEmpty() ? null : tiposPago.get(0))
                .caja(caja)
                .build();
        abono = abonoRepository.save(abono);

        guardarPagos(abono, request.pagos(), request.idTipoPago());

        registrarIngresoCajaAbono(caja, abono, usuario);

        TipoMovimientoCredito tipoMov = tipo == TipoAbono.LIQUIDACION ? TipoMovimientoCredito.LIQUIDACION : TipoMovimientoCredito.ABONO;

        MovimientoCredito mov = MovimientoCredito.builder()
                .credito(credito)
                .tipo(tipoMov)
                .monto(request.monto())
                .interesAplicado(interesPagado)
                .capitalAplicado(capitalPagado)
                .saldoAnterior(saldoAnterior)
                .saldoNuevo(saldoNuevo)
                .descripcion(tipo == TipoAbono.LIQUIDACION ? "Liquidacion total" : "Abono parcial")
                .fecha(LocalDateTime.now())
                .usuario(usuario)
                .tipoPago(tiposPago.isEmpty() ? null : tiposPago.get(0))
                .build();
        movimientoCreditoRepository.save(mov);

        credito.setSaldoInteres(saldoInteresNuevo);
        credito.setSaldoCapital(saldoCapitalNuevo);
        credito.setSaldoPendiente(saldoNuevo);
        if (tipo == TipoAbono.LIQUIDACION || saldoNuevo <= 0) {
            credito.setEstado(EstadoCredito.PAGADO);
        }
        creditoRepository.save(credito);

        // Update cliente saldoActual
        Cliente cliente = credito.getCliente();
        cliente.setSaldoActual(cliente.getSaldoActual() - request.monto());
        clienteRepository.save(cliente);

        aplicarPagoParcialidades(credito, interesPagado, capitalPagado);
        return toAbonoResponse(abono, usuario, saldoAnterior, saldoNuevo);
    }

    @Override
    @Transactional
    public List<AbonoResponse> abonarATodas(AbonoGeneralRequest request) {
        Cliente cliente = clienteRepository.findById(request.idCliente())
                .orElseThrow(() -> new NotFoundException("Cliente no encontrado"));

        List<Credito> activos = creditoRepository.findByClienteIdClienteAndEstadoOrderByFechaCreacionDesc(
                request.idCliente(), EstadoCredito.ACTIVO);

        if (activos.isEmpty()) {
            throw new InvalidEntryException("El cliente no tiene creditos activos");
        }

        double totalDeuda = activos.stream().mapToDouble(Credito::getSaldoPendiente).sum();
        if (request.monto() > totalDeuda) {
            throw new InvalidEntryException("El monto ($" + String.format("%.2f", request.monto())
                    + ") excede la deuda total ($" + String.format("%.2f", totalDeuda) + ")");
        }

        Persona usuario = obtenerPersonaActual();
        List<TipoPago> tiposPago = resolverTiposPago(request.pagos(), request.idTipoPago());
        Caja caja = resolverCaja(request.idCaja());
        List<AbonoResponse> resultados = new ArrayList<>();

        for (Credito credito : activos) {
            double proporcion = credito.getSaldoPendiente() / totalDeuda;
            double montoAbono = Math.round(request.monto() * proporcion * 100.0) / 100.0;
            if (montoAbono <= 0) continue;
            if (montoAbono > credito.getSaldoPendiente()) {
                montoAbono = credito.getSaldoPendiente();
            }

            prepararSaldosCredito(credito);
            double saldoAnterior = credito.getSaldoPendiente();
            double interesPagado = Math.min(montoAbono, credito.getSaldoInteres());
            double capitalPagado = montoAbono - interesPagado;
            double saldoInteresNuevo = credito.getSaldoInteres() - interesPagado;
            double saldoCapitalNuevo = credito.getSaldoCapital() - capitalPagado;
            double saldoNuevo = saldoInteresNuevo + saldoCapitalNuevo;

            TipoAbono tipo = saldoNuevo <= 0 ? TipoAbono.LIQUIDACION : TipoAbono.PARCIAL;

            Abono abono = Abono.builder()
                    .credito(credito)
                    .monto(montoAbono)
                    .interesPagado(interesPagado)
                    .capitalPagado(capitalPagado)
                    .tipo(tipo)
                    .fecha(LocalDateTime.now())
                    .usuario(usuario)
                    .tipoPago(tiposPago.isEmpty() ? null : tiposPago.get(0))
                    .caja(caja)
                    .build();
            abono = abonoRepository.save(abono);

            guardarPagosProporcional(abono, request.pagos(), request.idTipoPago(), montoAbono, request.monto());

            registrarIngresoCajaAbono(caja, abono, usuario);

            TipoMovimientoCredito tipoMov = tipo == TipoAbono.LIQUIDACION ? TipoMovimientoCredito.LIQUIDACION : TipoMovimientoCredito.ABONO;

            MovimientoCredito mov = MovimientoCredito.builder()
                    .credito(credito)
                    .tipo(tipoMov)
                    .monto(montoAbono)
                    .interesAplicado(interesPagado)
                    .capitalAplicado(capitalPagado)
                    .saldoAnterior(saldoAnterior)
                    .saldoNuevo(saldoNuevo)
                    .descripcion("Abono general - distribucion proporcional")
                    .fecha(LocalDateTime.now())
                    .usuario(usuario)
                    .tipoPago(tiposPago.isEmpty() ? null : tiposPago.get(0))
                    .build();
            movimientoCreditoRepository.save(mov);

            credito.setSaldoPendiente(saldoNuevo);
            credito.setSaldoInteres(saldoInteresNuevo);
            credito.setSaldoCapital(saldoCapitalNuevo);
            if (tipo == TipoAbono.LIQUIDACION || saldoNuevo <= 0) {
                credito.setEstado(EstadoCredito.PAGADO);
            }
            creditoRepository.save(credito);
            aplicarPagoParcialidades(credito, interesPagado, capitalPagado);

            resultados.add(toAbonoResponse(abono, usuario, saldoAnterior, saldoNuevo));
        }

        // Update cliente saldoActual
        cliente.setSaldoActual(cliente.getSaldoActual() - request.monto());
        clienteRepository.save(cliente);

        return resultados;
    }

    @Override
    public EstadoCuentaResponse estadoCuenta(Integer idCredito) {
        Credito credito = creditoRepository.findById(idCredito)
                .orElseThrow(() -> new NotFoundException("Credito no encontrado"));

        List<AbonoResponse> abonos = abonoRepository.findByCreditoIdCreditoOrderByFechaDesc(idCredito).stream()
                .map(a -> toAbonoResponse(a, a.getUsuario()))
                .toList();

        List<MovimientoCreditoResponse> movimientos = movimientoCreditoRepository
                .findByCreditoIdCreditoOrderByFechaDesc(idCredito).stream()
                .map(this::toMovimientoResponse).toList();

        Cliente c = credito.getCliente();
        boolean tieneIne = clienteIneRepository.findByClienteIdCliente(c.getIdCliente()).isPresent();
        ClienteResponse clienteResponse = new ClienteResponse(
                c.getIdCliente(), c.getNombre(), c.getApellidoPaterno(), c.getApellidoMaterno(),
                c.getTelefono(), c.getCodigoPais(), c.getWhatsapp(), c.getEmpresa(),
                c.getRegimenFiscal(), c.getCp(), c.getDireccion(),
                c.getCalle(), c.getNumExt(), c.getNumInt(), c.getColonia(),
                c.getMunicipio(), c.getEstado(),
                c.getRfc(), c.getRepresentanteLegal(), c.getDireccionEntrega(),
                c.getActivo(), c.getFechaRegistro(),
                c.getTieneCredito(), c.getLimiteCredito(), c.getSaldoActual(),
                c.getEnListaNegra(), c.getFechaListaNegra(), c.getMotivoListaNegra(),
                tieneIne);

        String titular = configuracionService.getValor("titularPagare", "PRISCILA ARONG KIM LOPEZ");
        double tasaMora = configuracionService.getValorDouble("tasaInteresMoraPagare", 5.0);

        return new EstadoCuentaResponse(
                toCreditoResponse(credito), clienteResponse,
                abonos, movimientos, titular, tasaMora);
    }

    @Override
    @Transactional
    public void condonarIntereses(Integer idCredito, CondonacionInteresRequest request) {
        Credito credito = creditoRepository.findById(idCredito)
                .orElseThrow(() -> new NotFoundException("Credito no encontrado"));
        prepararSaldosCredito(credito);
        if (request.monto() <= 0) throw new InvalidEntryException("El monto debe ser mayor a cero");
        if (request.monto() > credito.getSaldoInteres() + 0.001) {
            throw new InvalidEntryException("El monto excede el saldo de intereses");
        }

        double saldoAnterior = credito.getSaldoPendiente();
        aplicarCondonacionParcialidades(credito, request.monto());
        credito.setSaldoInteres(credito.getSaldoInteres() - request.monto());
        credito.setSaldoPendiente(credito.getSaldoCapital() + credito.getSaldoInteres());
        if (credito.getSaldoPendiente() <= 0.01) credito.setEstado(EstadoCredito.PAGADO);
        creditoRepository.save(credito);

        Persona usuario = obtenerPersonaActual();
        movimientoCreditoRepository.save(MovimientoCredito.builder()
                .credito(credito)
                .tipo(TipoMovimientoCredito.AJUSTE)
                .monto(request.monto())
                .interesAplicado(-request.monto())
                .capitalAplicado(0.0)
                .saldoAnterior(saldoAnterior)
                .saldoNuevo(credito.getSaldoPendiente())
                .descripcion("Condonacion de intereses: " + request.motivo())
                .fecha(LocalDateTime.now())
                .usuario(usuario)
                .build());
    }

    @Override
    public List<ParcialidadResponse> listarParcialidades(Integer idCredito) {
        return parcialidadCreditoRepository.findByCreditoIdCreditoOrderByNumeroAsc(idCredito).stream()
                .map(p -> new ParcialidadResponse(p.getIdParcialidad(), p.getNumero(), p.getFechaVencimiento(),
                        p.getCapitalProgramado(), p.getInteresProgramado(), p.getCapitalPagado(),
                        p.getInteresPagado(), p.getEstado()))
                .toList();
    }

    @Override
    public List<CobradorResponse> listarCobradores() {
        return personaRepository.findByActivaTrueOrderByNombreAsc().stream()
                .map(p -> new CobradorResponse(p.getIdPersona(), p.getNombre(), p.getApellido(), p.getUsuario()))
                .toList();
    }

    @Override
    @Transactional
    public void asignarCobrador(Integer idCredito, Integer idPersona) {
        Credito credito = creditoRepository.findById(idCredito)
                .orElseThrow(() -> new NotFoundException("Credito no encontrado"));
        Persona cobrador = idPersona == null ? null : personaRepository.findById(idPersona)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
        if (cobrador != null && !Boolean.TRUE.equals(cobrador.getActiva())) {
            throw new InvalidEntryException("El usuario seleccionado está inactivo");
        }
        credito.setCobrador(cobrador);
        creditoRepository.save(credito);
    }

    @Override
    public List<CobranzaResponse> agendaCobranza(LocalDate desde, LocalDate hasta, Integer idCobrador, String estado) {
        Persona actual = obtenerPersonaActual();
        boolean verTodas = tieneAutoridad("COBRANZA_VER_TODAS") || tieneAutoridad("CREDITOS_ASIGNAR");
        Integer cobradorFiltro = verTodas ? idCobrador : actual.getIdPersona();
        LocalDate inicio = desde != null ? desde : LocalDate.now();
        LocalDate fin = hasta != null ? hasta : inicio.plusDays(30);
        return parcialidadCreditoRepository.buscarAgenda(cobradorFiltro, inicio.atStartOfDay(), fin.plusDays(1).atStartOfDay(), estado)
                .stream().map(this::toCobranzaResponse).toList();
    }

    @Override
    @Transactional
    public AbonoResponse registrarAbonoCobranza(AbonoRequest request) {
        Credito credito = creditoRepository.findById(request.idCredito())
                .orElseThrow(() -> new NotFoundException("Credito no encontrado"));
        Persona actual = obtenerPersonaActual();
        boolean verTodas = tieneAutoridad("COBRANZA_VER_TODAS") || tieneAutoridad("CREDITOS_ASIGNAR");
        if (!verTodas && (credito.getCobrador() == null || !credito.getCobrador().getIdPersona().equals(actual.getIdPersona()))) {
            throw new InvalidEntryException("El credito no está asignado a este usuario");
        }
        if (request.idCaja() == null) {
            throw new InvalidEntryException("Debe seleccionar una caja abierta para registrar la cobranza");
        }
        return registrarAbono(request);
    }

    private boolean tieneAutoridad(String autoridad) {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> autoridad.equals(a.getAuthority()));
    }

    private CobranzaResponse toCobranzaResponse(ParcialidadCredito p) {
        Credito c = p.getCredito();
        Cliente cliente = c.getCliente();
        double programado = (p.getCapitalProgramado() == null ? 0 : p.getCapitalProgramado())
                + (p.getInteresProgramado() == null ? 0 : p.getInteresProgramado());
        double pagado = (p.getCapitalPagado() == null ? 0 : p.getCapitalPagado())
                + (p.getInteresPagado() == null ? 0 : p.getInteresPagado());
        String nombre = cliente.getNombre() + " " + (cliente.getApellidoPaterno() == null ? "" : cliente.getApellidoPaterno());
        String direccion = cliente.getDireccion();
        if (direccion == null || direccion.isBlank()) {
            direccion = String.join(", ", java.util.stream.Stream.of(cliente.getCalle(), cliente.getNumExt(), cliente.getColonia(), cliente.getMunicipio(), cliente.getEstado(), cliente.getCp())
                    .filter(v -> v != null && !v.isBlank()).toList());
        }
        return new CobranzaResponse(p.getIdParcialidad(), p.getNumero(), p.getFechaVencimiento(), programado, pagado,
                Math.max(0, programado - pagado), p.getEstado(), c.getIdCredito(), c.getFolio(), cliente.getIdCliente(),
                nombre, cliente.getTelefono(), direccion, c.getPeriodicidad(), c.getNumeroParcialidades(),
                c.getCobrador() == null ? null : c.getCobrador().getIdPersona(),
                c.getCobrador() == null ? null : c.getCobrador().getNombre() + " " + c.getCobrador().getApellido());
    }

    private Persona obtenerPersonaActual() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return personaRepository.findByUsuario(username)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));
    }

    private CreditoResponse toCreditoResponse(Credito c) {
        double capital = c.getCapitalFinanciado() != null ? c.getCapitalFinanciado() : c.getMontoOriginal();
        double interes = c.getInteresTotal() != null ? c.getInteresTotal() : 0.0;
        double saldoCapital = c.getSaldoCapital() != null ? c.getSaldoCapital() : c.getSaldoPendiente();
        double saldoInteres = c.getSaldoInteres() != null ? c.getSaldoInteres() : 0.0;
        return new CreditoResponse(
                c.getIdCredito(), c.getVenta().getIdVenta(),
                c.getVenta().getIdVenta(),
                c.getFolio(),
                c.getCliente().getIdCliente(),
                c.getCliente().getNombre() + " " + c.getCliente().getApellidoPaterno(),
                c.getMontoOriginal(), c.getSaldoPendiente(),
                c.getPlazoMeses(), c.getPorcentajeInteres(),
                c.getPeriodicidad(), c.getNumeroParcialidades(), c.getFechaPrimerPago(),
                capital, interes, saldoCapital, saldoInteres,
                c.getFechaVencimiento(), c.getEstado(), c.getFechaCreacion(),
                c.getVenta().getNota(),
                c.getCobrador() == null ? null : c.getCobrador().getIdPersona(),
                c.getCobrador() == null ? null : c.getCobrador().getNombre() + " " + c.getCobrador().getApellido());
    }

    private MovimientoCreditoResponse toMovimientoResponse(MovimientoCredito m) {
        return new MovimientoCreditoResponse(
                m.getIdMovimiento(), m.getCredito().getIdCredito(),
                m.getTipo(), m.getMonto(), m.getInteresAplicado(), m.getCapitalAplicado(),
                m.getSaldoAnterior(), m.getSaldoNuevo(),
                m.getDescripcion(), m.getFecha(),
                m.getUsuario().getUsuario(),
                m.getTipoPago() != null ? m.getTipoPago().getNombre() : null);
    }

    private TipoPago resolverTipoPago(Integer idTipoPago) {
        if (idTipoPago == null) {
            return null;
        }
        return tipoPagoRepository.findById(idTipoPago)
                .orElseThrow(() -> new NotFoundException("Tipo de pago no encontrado"));
    }

    private List<TipoPago> resolverTiposPago(List<AbonoPagoRequest> pagos, Integer idTipoPagoLegacy) {
        if (pagos == null || pagos.isEmpty()) {
            List<TipoPago> lista = new ArrayList<>();
            if (idTipoPagoLegacy != null) {
                lista.add(resolverTipoPago(idTipoPagoLegacy));
            }
            return lista;
        }
        double suma = pagos.stream().mapToDouble(p -> p.monto() != null ? p.monto() : 0.0).sum();
        if (Math.abs(suma - 0.0) < 0.000001) {
            throw new InvalidEntryException("El abono requiere al menos un pago con monto mayor a cero");
        }
        return pagos.stream().map(p -> resolverTipoPago(p.idTipoPago())).toList();
    }

    private List<AbonoPago> guardarPagos(Abono abono, List<AbonoPagoRequest> pagos, Integer idTipoPagoLegacy) {
        List<AbonoPago> guardados = new ArrayList<>();
        if (pagos == null || pagos.isEmpty()) {
            if (idTipoPagoLegacy != null) {
                guardados.add(abonoPagoRepository.save(AbonoPago.builder()
                        .abono(abono)
                        .tipoPago(resolverTipoPago(idTipoPagoLegacy))
                        .monto(abono.getMonto())
                        .build()));
            }
            return guardados;
        }
        double sumaPagos = pagos.stream().mapToDouble(p -> p.monto() != null ? p.monto() : 0.0).sum();
        if (Math.abs(sumaPagos - abono.getMonto()) > 0.01) {
            throw new InvalidEntryException("La suma de las formas de pago debe ser igual al monto del abono");
        }
        for (AbonoPagoRequest p : pagos) {
            if (p.monto() == null || p.monto() <= 0) continue;
            guardados.add(abonoPagoRepository.save(AbonoPago.builder()
                    .abono(abono)
                    .tipoPago(resolverTipoPago(p.idTipoPago()))
                    .monto(p.monto())
                    .referencia(p.referencia())
                    .build()));
        }
        return guardados;
    }

    private void guardarPagosProporcional(Abono abono, List<AbonoPagoRequest> pagos,
                                          Integer idTipoPagoLegacy, double montoAbono, double montoTotal) {
        if (pagos == null || pagos.isEmpty()) {
            if (idTipoPagoLegacy != null) {
                abonoPagoRepository.save(AbonoPago.builder()
                        .abono(abono)
                        .tipoPago(resolverTipoPago(idTipoPagoLegacy))
                        .monto(montoAbono)
                        .build());
            }
            return;
        }
        for (AbonoPagoRequest p : pagos) {
            if (p.monto() == null || p.monto() <= 0) continue;
            double montoProp = Math.round(p.monto() * montoAbono / montoTotal * 100.0) / 100.0;
            if (montoProp <= 0) continue;
            abonoPagoRepository.save(AbonoPago.builder()
                    .abono(abono)
                    .tipoPago(resolverTipoPago(p.idTipoPago()))
                    .monto(montoProp)
                    .referencia(p.referencia())
                    .build());
        }
    }

    private AbonoResponse toAbonoResponse(Abono a, Persona usuario) {
        return toAbonoResponse(a, usuario, null, null);
    }

    private AbonoResponse toAbonoResponse(Abono a, Persona usuario, Double saldoAnterior, Double saldoNuevo) {
        Credito credito = a.getCredito();
        Cliente cliente = credito.getCliente();
        return new AbonoResponse(
                a.getIdAbono(), credito.getIdCredito(),
                a.getMonto(), a.getTipo().name(),
                a.getFecha(), usuario != null ? usuario.getUsuario() : a.getUsuario().getUsuario(),
                a.getTipoPago() != null ? a.getTipoPago().getNombre() : null,
                abonoPagoRepository.findByAbonoIdAbono(a.getIdAbono()).stream()
                        .map(p -> new AbonoPagoResponse(
                                p.getIdAbonoPago(),
                                p.getTipoPago().getIdTipoPago(),
                                p.getTipoPago().getNombre(),
                                p.getMonto(),
                                p.getReferencia()))
                        .toList(),
                saldoAnterior,
                saldoNuevo,
                credito.getEstado().name(),
                cliente.getNombre() + " " + (cliente.getApellidoPaterno() != null ? cliente.getApellidoPaterno() : ""),
                credito.getVenta().getIdVenta(),
                credito.getFolio(),
                a.getInteresPagado(), a.getCapitalPagado(),
                credito.getSaldoInteres(), credito.getSaldoCapital());
    }

    private void prepararSaldosCredito(Credito credito) {
        if (credito.getSaldoCapital() == null) {
            credito.setSaldoCapital(credito.getSaldoPendiente() != null ? credito.getSaldoPendiente() : 0.0);
        }
        if (credito.getSaldoInteres() == null) credito.setSaldoInteres(0.0);
        if (credito.getCapitalFinanciado() == null) credito.setCapitalFinanciado(credito.getSaldoCapital());
        if (credito.getInteresTotal() == null) credito.setInteresTotal(credito.getSaldoInteres());
        if (parcialidadCreditoRepository.findByCreditoIdCreditoOrderByNumeroAsc(credito.getIdCredito()).isEmpty()) {
            parcialidadCreditoRepository.save(ParcialidadCredito.builder()
                    .credito(credito)
                    .numero(1)
                    .fechaVencimiento(credito.getFechaVencimiento())
                    .capitalProgramado(credito.getSaldoCapital())
                    .interesProgramado(credito.getSaldoInteres())
                    .build());
        }
    }

    private void aplicarPagoParcialidades(Credito credito, double interes, double capital) {
        List<ParcialidadCredito> parcialidades = parcialidadCreditoRepository
                .findByCreditoIdCreditoOrderByNumeroAsc(credito.getIdCredito());
        double interesRestante = interes;
        double capitalRestante = capital;
        for (ParcialidadCredito parcialidad : parcialidades) {
            double interesPendiente = Math.max(0, parcialidad.getInteresProgramado() - parcialidad.getInteresPagado());
            double aplicarInteres = Math.min(interesRestante, interesPendiente);
            parcialidad.setInteresPagado(parcialidad.getInteresPagado() + aplicarInteres);
            interesRestante -= aplicarInteres;

            double capitalPendiente = Math.max(0, parcialidad.getCapitalProgramado() - parcialidad.getCapitalPagado());
            double aplicarCapital = Math.min(capitalRestante, capitalPendiente);
            parcialidad.setCapitalPagado(parcialidad.getCapitalPagado() + aplicarCapital);
            capitalRestante -= aplicarCapital;

            if (parcialidad.getCapitalPagado() + 0.001 >= parcialidad.getCapitalProgramado()
                    && parcialidad.getInteresPagado() + 0.001 >= parcialidad.getInteresProgramado()) {
                parcialidad.setEstado("PAGADA");
            } else if (parcialidad.getCapitalPagado() > 0 || parcialidad.getInteresPagado() > 0) {
                parcialidad.setEstado("PARCIAL");
            }
            parcialidadCreditoRepository.save(parcialidad);
            if (interesRestante <= 0.001 && capitalRestante <= 0.001) break;
        }
    }

    private void aplicarCondonacionParcialidades(Credito credito, double monto) {
        double restante = monto;
        for (ParcialidadCredito parcialidad : parcialidadCreditoRepository
                .findByCreditoIdCreditoOrderByNumeroAsc(credito.getIdCredito())) {
            double pendiente = Math.max(0, parcialidad.getInteresProgramado() - parcialidad.getInteresPagado());
            double condonar = Math.min(restante, pendiente);
            parcialidad.setInteresProgramado(parcialidad.getInteresProgramado() - condonar);
            if (parcialidad.getCapitalPagado() + 0.001 >= parcialidad.getCapitalProgramado()
                    && parcialidad.getInteresPagado() + 0.001 >= parcialidad.getInteresProgramado()) {
                parcialidad.setEstado("PAGADA");
            }
            parcialidadCreditoRepository.save(parcialidad);
            restante -= condonar;
            if (restante <= 0.001) break;
        }
    }

    private Caja resolverCaja(Integer idCaja) {
        if (idCaja == null) {
            return null;
        }
        return cajaRepository.findById(idCaja)
                .orElseThrow(() -> new NotFoundException("Caja no encontrada"));
    }

    private void registrarIngresoCajaAbono(Caja caja, Abono abono, Persona usuario) {
        if (caja == null) {
            return;
        }
        if (caja.getEstado() != CajaEstado.ABIERTA) {
            throw new InvalidEntryException("La caja no está abierta");
        }
        movimientoCajaRepository.save(MovimientoCaja.builder()
                .caja(caja)
                .tipo(TipoMovimientoCaja.INGRESO)
                .monto(abono.getMonto())
                .motivo("Abono a credito #" + abono.getCredito().getIdCredito())
                .usuario(usuario)
                .fecha(LocalDateTime.now())
                .build());
        caja.setSaldoActual(caja.getSaldoActual() + abono.getMonto());
        cajaRepository.save(caja);
    }
}
