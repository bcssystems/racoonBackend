package com.bcsystems.bonds.controller;

import com.bcsystems.bonds.dto.CodigoPostalResponse;
import com.bcsystems.bonds.service.CodigoPostalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/catalogos")
public class CodigoPostalController {
    private final CodigoPostalService codigoPostalService;
    @Value("${google.maps.browser-api-key:}") private String browserApiKey;
    @Value("${google.maps.map-id:DEMO_MAP_ID}") private String googleMapsMapId;

    @GetMapping("/codigos-postales/{cp}")
    @PreAuthorize("hasAnyAuthority('CLIENTES_VER','CLIENTES_CREAR','CLIENTES_EDITAR','VENTAS_VER','VENTAS_CREAR')")
    public ResponseEntity<CodigoPostalResponse> consultar(@PathVariable String cp) {
        return ResponseEntity.ok(codigoPostalService.consultar(cp));
    }

    @GetMapping("/mapa-config")
    @PreAuthorize("hasAnyAuthority('CLIENTES_VER','CLIENTES_CREAR','CLIENTES_EDITAR','COBRANZA_VER')")
    public ResponseEntity<Map<String, String>> mapaConfig() {
        return ResponseEntity.ok(Map.of("googleMapsApiKey", browserApiKey == null ? "" : browserApiKey,
                "googleMapsMapId", googleMapsMapId == null ? "DEMO_MAP_ID" : googleMapsMapId));
    }
}
