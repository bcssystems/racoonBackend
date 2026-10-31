package com.bcsystems.bonds.controller;

import com.bcsystems.bonds.dto.CatalogoCategoriaResponse;
import com.bcsystems.bonds.dto.CatalogoProductoResponse;
import com.bcsystems.bonds.service.CatalogoService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/catalogo")
public class EcommerceCatalogoController {
    private final CatalogoService catalogoService;

    public EcommerceCatalogoController(CatalogoService catalogoService) {
        this.catalogoService = catalogoService;
    }

    @GetMapping("/categorias")
    public ResponseEntity<List<CatalogoCategoriaResponse>> categorias() {
        return ResponseEntity.ok(catalogoService.categorias());
    }

    @GetMapping("/productos")
    public ResponseEntity<Page<CatalogoProductoResponse>> productos(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer idCategoria,
            @PageableDefault(size = 24, sort = "nombre", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(catalogoService.productos(search, idCategoria, pageable));
    }

    @GetMapping("/productos/{id}")
    public ResponseEntity<CatalogoProductoResponse> producto(@PathVariable Integer id) {
        return ResponseEntity.ok(catalogoService.producto(id));
    }
}
