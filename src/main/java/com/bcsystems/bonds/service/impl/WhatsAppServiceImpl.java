package com.bcsystems.bonds.service.impl;

import com.bcsystems.bonds.domain.*;
import com.bcsystems.bonds.repository.*;
import com.bcsystems.bonds.service.WhatsAppService;
import com.bcsystems.bonds.event.CreditoAsignadoEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import java.text.NumberFormat;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WhatsAppServiceImpl implements WhatsAppService {
    private final MensajeWhatsAppRepository mensajeRepository;
    private final CreditoRepository creditoRepository;
    private final PersonaRepository personaRepository;
    private final ParcialidadCreditoRepository parcialidadRepository;
    private final VentaDetalleRepository detalleRepository;
    private final VentaPagoRepository pagoRepository;

    @Value("${wasender.enabled:false}") private boolean enabled;
    @Value("${wasender.api-url:https://wasenderapi.com/api}") private String apiUrl;
    @Value("${wasender.api-token:}") private String apiToken;
    @Value("${wasender.country-code:52}") private String countryCode;
    @Value("${wasender.time-zone:America/Mexico_City}") private String timeZone;

    private RestClient restClient = RestClient.builder().build();
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async("messagingExecutor")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void notificarCreditoAsignado(CreditoAsignadoEvent event) {
        Integer idCredito = event.idCredito();
        Integer idCobradorAnterior = event.idCobradorAnterior();
        Integer idCobradorNuevo = event.idCobradorNuevo();
        Credito credito = creditoRepository.findById(idCredito).orElse(null);
        if (credito == null) return;
        if (idCobradorAnterior != null && !idCobradorAnterior.equals(idCobradorNuevo)) {
            Persona anterior = personaRepository.findById(idCobradorAnterior).orElse(null);
            if (anterior != null) enviar(anterior.getTelefono(), "REASIGNACION_RETIRO", "credito:" + idCredito,
                    "⚠️ COBRANZA REASIGNADA\n\nHola, " + nombre(anterior) + ".\n\nEl crédito " +
                            valor(credito.getFolio(), "#" + idCredito) + " del cliente " + nombre(credito.getCliente()) +
                            " ya no se encuentra asignado a tu cartera.");
        }
        Persona nuevo = credito.getCobrador();
        if (nuevo != null && (idCobradorAnterior == null || !nuevo.getIdPersona().equals(idCobradorAnterior))) {
            enviar(nuevo.getTelefono(), "CREDITO_ASIGNADO", "credito:" + idCredito, mensajeCredito(credito, nuevo));
        }
    }

    @Override
    @Async("messagingExecutor")
    @Transactional
    public void notificarAutorizacion(String tipo, String referencia, String detalle, String codigo, String solicitante) {
        String mensaje = "🔐 AUTORIZACIÓN ADMINISTRATIVA\n\nTipo: " + tipo + "\nReferencia: " + referencia +
                "\nSolicitante: " + valor(solicitante, "No disponible") + "\nDetalle: " + valor(detalle, "Sin detalle") +
                "\n\nCódigo: " + codigo + "\nVigencia: 10 minutos.";
        for (Persona admin : personaRepository.findByRolNombreIgnoreCaseAndActivaTrue("ADMINISTRADOR")) {
            enviar(admin.getTelefono(), "AUTORIZACION", referencia, mensaje);
        }
    }

    @Override
    @Scheduled(cron = "0 0 8 * * *", zone = "${wasender.time-zone:America/Mexico_City}")
    @Async("messagingExecutor")
    @Transactional(readOnly = true)
    public void enviarItinerarios() {
        LocalDate hoy = LocalDate.now(ZoneId.of(timeZone));
        LocalDateTime limite = hoy.plusDays(1).atStartOfDay();
        for (Persona cobrador : creditoRepository.findCobradoresActivos()) {
            List<ParcialidadCredito> items = parcialidadRepository.findPendientesParaItinerario(cobrador.getIdPersona(), limite);
            if (!items.isEmpty()) enviar(cobrador.getTelefono(), "ITINERARIO", "cobrador:" + cobrador.getIdPersona(), mensajeItinerario(cobrador, items, hoy));
        }
    }

    private String mensajeCredito(Credito c, Persona cobrador) {
        Cliente cliente = c.getCliente(); Venta venta = c.getVenta();
        String productos = detalleRepository.findByVentaIdVenta(venta.getIdVenta()).stream()
                .map(d -> "- " + d.getCantidad() + " x " + valor(d.getProducto() == null ? d.getDescripcion() : d.getProducto().getNombre(), "Producto") + " ($" + dinero(d.getSubtotal()) + ")")
                .collect(Collectors.joining("\n"));
        String pagos = pagoRepository.findByVentaIdVenta(venta.getIdVenta()).stream()
                .map(p -> "- " + valor(p.getTipoPago() == null ? null : p.getTipoPago().getNombre(), "Pago") + ": $" + dinero(p.getMonto()))
                .collect(Collectors.joining("\n"));
        return "📋 NUEVO CRÉDITO ASIGNADO\n\nHola, " + nombre(cobrador) + ".\n\n👤 CLIENTE\n" + nombre(cliente) +
                "\n📍 Dirección: " + direccion(cliente) + "\n\n🧾 VENTA: #" + venta.getIdVenta() + "\n🛒 PRODUCTOS\n" +
                valor(productos, "Sin productos") + "\n\n💳 MÉTODOS DE PAGO\n" + valor(pagos, "Sin pago inicial") +
                "\n\n💰 TOTAL A FINANCIAR: $" + dinero(c.getCapitalFinanciado() == null ? c.getMontoOriginal() : c.getCapitalFinanciado()) +
                "\n📝 Nota: " + valor(venta.getNota(), "Sin nota") + "\n\n⚠️ FAVOR DE COMUNICARSE LO ANTES POSIBLE CON EL CLIENTE.";
    }

    private String mensajeItinerario(Persona cobrador, List<ParcialidadCredito> items, LocalDate hoy) {
        StringBuilder s = new StringBuilder("🗓️ ITINERARIO DE COBRANZA\n\nHola, ").append(nombre(cobrador)).append(".\n\n");
        appendAgenda(s, "✅ COBROS DE HOY", items.stream().filter(p -> !p.getFechaVencimiento().toLocalDate().isBefore(hoy)).toList(), false);
        appendAgenda(s, "⚠️ COBROS ATRASADOS", items.stream().filter(p -> p.getFechaVencimiento().toLocalDate().isBefore(hoy)).toList(), true);
        return s.toString();
    }

    private void appendAgenda(StringBuilder s, String title, List<ParcialidadCredito> items, boolean overdue) {
        if (items.isEmpty()) return;
        s.append(title).append("\n\n");
        for (ParcialidadCredito p : items) {
            Credito c = p.getCredito();
            double amount = (p.getCapitalProgramado() == null ? 0 : p.getCapitalProgramado()) + (p.getInteresProgramado() == null ? 0 : p.getInteresProgramado());
            s.append("• Crédito: ").append(valor(c.getFolio(), "#" + c.getIdCredito())).append("\n  Cliente: ").append(nombre(c.getCliente()))
                    .append("\n  Abono: $").append(dinero(amount)).append("\n  Estado: ").append(overdue ? "ATRASADO" : "NORMAL")
                    .append("\n  Vencimiento: ").append(p.getFechaVencimiento().format(DATE)).append("\n  Dirección: ").append(direccion(c.getCliente())).append("\n\n");
        }
    }

    private void enviar(String telefono, String tipo, String referencia, String texto) {
        MensajeWhatsApp m = mensajeRepository.save(MensajeWhatsApp.builder().tipo(tipo).referencia(referencia)
                .destinatario(normalizar(telefono)).mensaje(texto).estado("PENDIENTE").intentos(0).fechaCreacion(LocalDateTime.now()).build());
        if (!enabled || apiToken == null || apiToken.isBlank()) { m.setEstado("DESHABILITADO"); mensajeRepository.save(m); return; }
        if (m.getDestinatario() == null) { m.setEstado("ERROR"); m.setDetalleError("Teléfono no válido"); mensajeRepository.save(m); return; }
        RestClientException last = null;
        for (int i = 1; i <= 3; i++) {
            try {
                m.setIntentos(i);
                restClient.post().uri(apiUrl.replaceAll("/$", "") + "/send-message").header("Authorization", "Bearer " + apiToken)
                        .body(Map.of("to", m.getDestinatario(), "text", texto)).retrieve().toBodilessEntity();
                m.setEstado("ENVIADO"); m.setFechaEnvio(LocalDateTime.now()); m.setDetalleError(null); mensajeRepository.save(m); return;
            } catch (RestClientException ex) {
                last = ex;
                if (i < 3) try { Thread.sleep(1000L * i); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
            }
        }
        m.setEstado("ERROR"); m.setDetalleError(last == null ? "Error desconocido" : last.getMessage()); mensajeRepository.save(m);
    }

    private String normalizar(String phone) {
        if (phone == null || phone.isBlank()) return null;
        String d = phone.replaceAll("\\D", ""); if (d.startsWith("00")) d = d.substring(2); if (d.length() == 10) d = countryCode + d; return d.length() >= 10 ? d : null;
    }
    private String nombre(Persona p) { return p == null ? "Sin nombre" : nombre(p.getNombre(), p.getApellido()); }
    private String nombre(Cliente c) { return c == null ? "Sin cliente" : nombre(c.getNombre(), c.getApellidoPaterno()); }
    private String nombre(String a, String b) { return valor(a, "") + (b == null || b.isBlank() ? "" : " " + b); }
    private String direccion(Cliente c) { if (c == null) return "Sin dirección"; if (c.getDireccion() != null && !c.getDireccion().isBlank()) return c.getDireccion(); return String.join(", ", Arrays.asList(c.getCalle(), c.getNumExt(), c.getNumInt(), c.getColonia(), c.getMunicipio(), c.getEstado(), c.getCp()).stream().filter(v -> v != null && !v.isBlank()).toList()); }
    private String dinero(Double v) { return NumberFormat.getNumberInstance(Locale.US).format(v == null ? 0 : v); }
    private String valor(String v, String fallback) { return v == null || v.isBlank() ? fallback : v; }
}
