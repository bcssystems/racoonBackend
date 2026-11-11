package com.bcsystems.bonds.controller;

import com.bcsystems.bonds.dto.*;
import com.bcsystems.bonds.service.SolicitudCreditoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/solicitudes-credito")
public class SolicitudCreditoController {
    private final SolicitudCreditoService service;
    @PostMapping @PreAuthorize("hasAuthority('CREDITOS_SOLICITAR')")
    public ResponseEntity<SolicitudCreditoResponse> crear(@Valid @RequestBody SolicitudCreditoRequest r) { return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(r)); }
    @GetMapping @PreAuthorize("hasAuthority('CREDITOS_SOLICITUDES_VER')")
    public ResponseEntity<List<SolicitudCreditoResponse>> listar(@RequestParam(required=false) String estado, @RequestParam(required=false) Integer idCliente) { return ResponseEntity.ok(service.listar(estado, idCliente)); }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('CREDITOS_SOLICITUDES_VER')")
    public ResponseEntity<SolicitudCreditoResponse> obtener(@PathVariable Integer id) { return ResponseEntity.ok(service.obtener(id)); }
    @PostMapping(value="/{id}/documentos", consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @PreAuthorize("hasAuthority('CREDITOS_DOCUMENTOS_CARGAR')")
    public ResponseEntity<SolicitudCreditoDocumentoResponse> documento(@PathVariable Integer id, @RequestParam String tipo, @RequestParam MultipartFile archivo) { return ResponseEntity.status(HttpStatus.CREATED).body(service.agregarDocumento(id, tipo, archivo)); }
    @PutMapping("/{id}/resolver") @PreAuthorize("hasAnyAuthority('CREDITOS_SOLICITUDES_AUTORIZAR','CREDITOS_SOLICITUDES_RECHAZAR')")
    public ResponseEntity<SolicitudCreditoResponse> resolver(@PathVariable Integer id, @RequestBody SolicitudCreditoResolverRequest r) { return ResponseEntity.ok(service.resolver(id, r)); }
}
