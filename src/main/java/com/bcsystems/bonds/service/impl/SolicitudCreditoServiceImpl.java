package com.bcsystems.bonds.service.impl;

import com.bcsystems.bonds.domain.*;
import com.bcsystems.bonds.dto.*;
import com.bcsystems.bonds.exception.InvalidEntryException;
import com.bcsystems.bonds.exception.NotFoundException;
import com.bcsystems.bonds.repository.*;
import com.bcsystems.bonds.service.SolicitudCreditoService;
import com.bcsystems.bonds.service.AuditoriaService;
import com.bcsystems.bonds.event.SolicitudCreditoCreadaEvent;
import org.springframework.context.ApplicationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

@Service
@RequiredArgsConstructor
public class SolicitudCreditoServiceImpl implements SolicitudCreditoService {
    private final SolicitudCreditoRepository solicitudRepository;
    private final SolicitudCreditoDocumentoRepository documentoRepository;
    private final ClienteRepository clienteRepository;
    private final PersonaRepository personaRepository;
    private final AuditoriaService auditoriaService;
    private final ApplicationEventPublisher eventPublisher;
    @Value("${app.upload.private-dir:./uploads/privados}") private String uploadDir;

    @Override @Transactional
    public SolicitudCreditoResponse crear(SolicitudCreditoRequest request) {
        if (request.idCliente() == null || request.montoSolicitado() == null || request.montoSolicitado() <= 0)
            throw new InvalidEntryException("Cliente y monto solicitado son obligatorios");
        Cliente cliente = clienteRepository.findById(request.idCliente()).orElseThrow(() -> new NotFoundException("Cliente no encontrado"));
        if (Boolean.TRUE.equals(cliente.getEnListaNegra())) throw new InvalidEntryException("El cliente está en lista negra");
        String tipo = "AUMENTO_LINEA".equalsIgnoreCase(request.tipoSolicitud()) ? "AUMENTO_LINEA" : "NUEVA";
        List<SolicitudCredito> anteriores = solicitudRepository.findByClienteIdClienteOrderByFechaSolicitudDesc(request.idCliente());
        if (tipo.equals("AUMENTO_LINEA")) {
            if (!Boolean.TRUE.equals(cliente.getTieneCredito())) throw new InvalidEntryException("El cliente no tiene una línea de crédito aprobada");
            double limiteActual = cliente.getLimiteCredito() == null ? 0 : cliente.getLimiteCredito();
            if (request.montoSolicitado() <= limiteActual) throw new InvalidEntryException("El nuevo límite debe ser mayor al límite actual");
            if (anteriores.stream().anyMatch(s -> "PENDIENTE".equals(s.getEstado()) && "AUMENTO_LINEA".equals(s.getTipoSolicitud())))
                throw new InvalidEntryException("El cliente ya tiene un aumento de línea pendiente");
        } else {
            if (Boolean.TRUE.equals(cliente.getTieneCredito())) throw new InvalidEntryException("El cliente ya tiene un crédito aprobado");
            if (anteriores.stream().anyMatch(s -> "PENDIENTE".equals(s.getEstado()) || "APROBADA".equals(s.getEstado())))
                throw new InvalidEntryException("El cliente ya tiene una solicitud pendiente o un crédito aprobado");
        }
        Persona solicitante = usuarioActual();
        double sugerido = tipo.equals("AUMENTO_LINEA") ? request.montoSolicitado() : calcularSugerido(request.encuestaJson(), request.montoSolicitado(), cliente.getScoreCredito());
        SolicitudCredito solicitud = SolicitudCredito.builder().cliente(cliente).solicitante(solicitante)
                .tipoSolicitud(tipo).limiteAnterior(tipo.equals("AUMENTO_LINEA") ? cliente.getLimiteCredito() : null)
                .montoSolicitado(request.montoSolicitado()).montoSugerido(sugerido)
                .encuestaJson(request.encuestaJson())
                .observaciones(request.observaciones()).build();
        solicitud = solicitudRepository.save(solicitud);
        auditoriaService.registrar("SolicitudCredito", solicitud.getIdSolicitud(), "CREACION", solicitante.getUsuario(), "Solicitud de crédito creada");
        if ("AUMENTO_LINEA".equals(tipo) && esAdministrador(solicitante)) {
            aprobarAutomaticamenteComoAdministrador(solicitud);
        }
        eventPublisher.publishEvent(new SolicitudCreditoCreadaEvent(solicitud.getIdSolicitud()));
        return toResponse(solicitud);
    }

    @Override @Transactional(readOnly = true)
    public List<SolicitudCreditoResponse> listar(String estado, Integer idCliente) {
        List<SolicitudCredito> lista = idCliente != null ? solicitudRepository.findByClienteIdClienteOrderByFechaSolicitudDesc(idCliente) : solicitudRepository.findAllByOrderByFechaSolicitudDesc();
        return lista.stream().filter(s -> estado == null || estado.isBlank() || estado.equals(s.getEstado())).map(this::toResponse).toList();
    }

    @Override @Transactional(readOnly = true)
    public SolicitudCreditoResponse obtener(Integer id) { return toResponse(solicitudRepository.findById(id).orElseThrow(() -> new NotFoundException("Solicitud no encontrada"))); }

    @Override @Transactional
    public SolicitudCreditoDocumentoResponse agregarDocumento(Integer id, String tipo, MultipartFile archivo) {
        SolicitudCredito solicitud = solicitudRepository.findById(id).orElseThrow(() -> new NotFoundException("Solicitud no encontrada"));
        if (archivo == null || archivo.isEmpty()) throw new InvalidEntryException("Selecciona un archivo");
        if (!Set.of("INE_FRONTAL", "INE_TRASERA", "DOMICILIO", "PAGARE").contains(tipo)) throw new InvalidEntryException("Tipo de documento inválido");
        String mime = archivo.getContentType() == null ? "" : archivo.getContentType().toLowerCase(Locale.ROOT);
        if (!(mime.startsWith("image/") || "application/pdf".equals(mime))) throw new InvalidEntryException("Solo se permiten imágenes o PDF");
        try {
            Path dir = Paths.get(uploadDir, "solicitudes-credito", String.valueOf(id)).toAbsolutePath().normalize(); Files.createDirectories(dir);
            String original = archivo.getOriginalFilename() == null ? "documento" : Paths.get(archivo.getOriginalFilename()).getFileName().toString();
            String ext = original.contains(".") ? original.substring(original.lastIndexOf('.')) : "";
            String nombre = UUID.randomUUID() + "-" + tipo.toLowerCase(Locale.ROOT) + ext;
            Path destino = dir.resolve(nombre).normalize();
            if (!destino.startsWith(dir)) throw new InvalidEntryException("Nombre de archivo inválido");
            Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
            SolicitudCreditoDocumento doc = documentoRepository.save(SolicitudCreditoDocumento.builder().solicitud(solicitud).tipo(tipo).url("solicitudes-credito/" + id + "/" + nombre).nombreArchivo(original).mimeType(mime).build());
            autoAprobarSiCorresponde(solicitud);
            return documentoResponse(doc);
        } catch (IOException e) { throw new InvalidEntryException("No se pudo guardar el documento"); }
    }

    @Override @Transactional(readOnly = true)
    public Resource descargarDocumento(Integer idSolicitud, Integer idDocumento) {
        SolicitudCreditoDocumento documento = documentoRepository.findById(idDocumento)
                .filter(d -> d.getSolicitud().getIdSolicitud().equals(idSolicitud))
                .orElseThrow(() -> new NotFoundException("Documento no encontrado"));
        if (documento.getUrl() == null || documento.getUrl().startsWith("/") || documento.getUrl().contains(".."))
            throw new NotFoundException("Documento no encontrado");
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path file = root.resolve(documento.getUrl()).normalize();
        if (!file.startsWith(root)) throw new NotFoundException("Documento no encontrado");
        Resource resource = new FileSystemResource(file);
        if (!resource.exists()) throw new NotFoundException("Archivo no encontrado");
        return resource;
    }

    @Override @Transactional
    public SolicitudCreditoResponse resolver(Integer id, SolicitudCreditoResolverRequest request) {
        if (!puedeResolver(request.aprobar())) throw new InvalidEntryException("No tienes permiso para resolver solicitudes de crédito");
        SolicitudCredito s = solicitudRepository.findById(id).orElseThrow(() -> new NotFoundException("Solicitud no encontrada"));
        if (!"PENDIENTE".equals(s.getEstado())) throw new InvalidEntryException("La solicitud ya fue resuelta");
        Persona resolutor = usuarioActual();
        boolean administrador = esAdministrador(resolutor);
        if (request.aprobar()) {
            Set<String> requeridos = "AUMENTO_LINEA".equals(s.getTipoSolicitud())
                    ? Set.of("PAGARE") : Set.of("INE_FRONTAL", "INE_TRASERA", "DOMICILIO", "PAGARE");
            Set<String> cargados = new HashSet<>();
            documentoRepository.findBySolicitudIdSolicitudOrderByFechaCargaAsc(id).forEach(d -> cargados.add(d.getTipo()));
            if (!cargados.containsAll(requeridos) && !administrador)
                throw new InvalidEntryException("La solicitud requiere documentos completos o autorización de un administrador");
            double monto = request.montoAprobado() == null ? s.getMontoSugerido() : request.montoAprobado();
            if ("AUMENTO_LINEA".equals(s.getTipoSolicitud()) && monto <= (s.getLimiteAnterior() == null ? 0 : s.getLimiteAnterior()))
                throw new InvalidEntryException("El nuevo límite debe ser mayor al límite anterior");
            String observaciones = request.observaciones();
            if (!cargados.containsAll(requeridos)) {
                observaciones = (observaciones == null || observaciones.isBlank() ? "" : observaciones + " ")
                        + "Aprobación administrativa sin documentos completos.";
            }
            aprobar(s, monto, observaciones);
        } else { s.setEstado("RECHAZADA"); s.setObservaciones(request.observaciones()); }
        s.setResolutor(resolutor); s.setFechaResolucion(LocalDateTime.now());
        solicitudRepository.save(s);
        auditoriaService.registrar("SolicitudCredito", id, "RESOLUCION", resolutor.getUsuario(), s.getEstado());
        return toResponse(s);
    }

    private void aprobar(SolicitudCredito s, double monto, String observacion) {
        if (monto <= 0) throw new InvalidEntryException("El monto aprobado debe ser mayor a cero");
        s.setEstado("APROBADA"); s.setMontoAprobado(monto); s.setObservaciones(observacion);
        s.getCliente().setTieneCredito(true); s.getCliente().setLimiteCredito(monto); clienteRepository.save(s.getCliente());
    }

    private void autoAprobarSiCorresponde(SolicitudCredito solicitud) {
        if (!"PENDIENTE".equals(solicitud.getEstado()) || !esAdministrador(solicitud.getSolicitante())) return;
        Set<String> requeridos = "AUMENTO_LINEA".equals(solicitud.getTipoSolicitud())
                ? Set.of("PAGARE") : Set.of("INE_FRONTAL", "INE_TRASERA", "DOMICILIO", "PAGARE");
        Set<String> cargados = new HashSet<>();
        documentoRepository.findBySolicitudIdSolicitudOrderByFechaCargaAsc(solicitud.getIdSolicitud()).forEach(d -> cargados.add(d.getTipo()));
        if (!cargados.containsAll(requeridos)) return;
        aprobar(solicitud, solicitud.getMontoSolicitado(), "Aprobación automática de administrador");
        solicitud.setResolutor(solicitud.getSolicitante());
        solicitud.setFechaResolucion(LocalDateTime.now());
        solicitudRepository.save(solicitud);
        auditoriaService.registrar("SolicitudCredito", solicitud.getIdSolicitud(), "RESOLUCION_AUTO", solicitud.getSolicitante().getUsuario(), solicitud.getEstado());
    }

    private void aprobarAutomaticamenteComoAdministrador(SolicitudCredito solicitud) {
        aprobar(solicitud, solicitud.getMontoSolicitado(), "Aprobación automática administrativa sin documentos");
        solicitud.setResolutor(solicitud.getSolicitante());
        solicitud.setFechaResolucion(LocalDateTime.now());
        solicitudRepository.save(solicitud);
        auditoriaService.registrar("SolicitudCredito", solicitud.getIdSolicitud(), "RESOLUCION_AUTO",
                solicitud.getSolicitante().getUsuario(), solicitud.getEstado());
    }

    private boolean esAdministrador(Persona persona) {
        return persona != null && persona.getRol() != null && "ADMINISTRADOR".equalsIgnoreCase(persona.getRol().getNombre());
    }

    private double calcularSugerido(String json, double solicitado, Integer score) {
        double capacidad = numero(json, "capacidadMensual");
        double ingreso = numero(json, "ingresoMensual");
        double base = capacidad > 0 ? capacidad * 8 : ingreso > 0 ? ingreso * .25 * 8 : solicitado;
        double factor = score != null && score < 70 ? .5 : score != null && score < 85 ? .75 : 1.0;
        return Math.max(0, Math.min(solicitado, Math.round(base * factor * 100) / 100.0));
    }
    private double numero(String json, String campo) { if (json == null) return 0; Matcher m = Pattern.compile("\\\"" + campo + "\\\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)").matcher(json); return m.find() ? Double.parseDouble(m.group(1)) : 0; }
    private boolean puedeAutorizar() { Authentication a = SecurityContextHolder.getContext().getAuthentication(); return a != null && a.getAuthorities().stream().anyMatch(x -> "CREDITOS_SOLICITUDES_AUTORIZAR".equals(x.getAuthority())); }
    private boolean puedeResolver(boolean aprobar) { Authentication a = SecurityContextHolder.getContext().getAuthentication(); String permiso = aprobar ? "CREDITOS_SOLICITUDES_AUTORIZAR" : "CREDITOS_SOLICITUDES_RECHAZAR"; return a != null && a.getAuthorities().stream().anyMatch(x -> permiso.equals(x.getAuthority())); }
    private Persona usuarioActual() { String u = SecurityContextHolder.getContext().getAuthentication().getName(); return personaRepository.findByUsuario(u).orElseThrow(() -> new NotFoundException("Usuario no encontrado")); }
    private SolicitudCreditoDocumentoResponse documentoResponse(SolicitudCreditoDocumento d) {
        return new SolicitudCreditoDocumentoResponse(d.getIdDocumento(), d.getTipo(),
                "/api/v1/solicitudes-credito/" + d.getSolicitud().getIdSolicitud() + "/documentos/" + d.getIdDocumento() + "/descarga",
                d.getNombreArchivo(), d.getMimeType(), d.getFechaCarga());
    }

    private SolicitudCreditoResponse toResponse(SolicitudCredito s) { List<SolicitudCreditoDocumentoResponse> docs = documentoRepository.findBySolicitudIdSolicitudOrderByFechaCargaAsc(s.getIdSolicitud()).stream().map(this::documentoResponse).toList(); return new SolicitudCreditoResponse(s.getIdSolicitud(), s.getCliente().getIdCliente(), (s.getCliente().getNombre() + " " + s.getCliente().getApellidoPaterno()).trim(), s.getEstado(), s.getMontoSolicitado(), s.getMontoSugerido(), s.getMontoAprobado(), s.getTipoSolicitud() == null ? "NUEVA" : s.getTipoSolicitud(), s.getLimiteAnterior(), s.getEncuestaJson(), s.getObservaciones(), s.getSolicitante().getUsuario(), s.getResolutor() == null ? null : s.getResolutor().getUsuario(), s.getFechaSolicitud(), s.getFechaResolucion(), docs); }
}
