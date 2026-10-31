package com.bcsystems.bonds.service;

import com.bcsystems.bonds.dto.CatalogoCategoriaResponse;
import com.bcsystems.bonds.dto.CatalogoProductoResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CatalogoService {
    List<CatalogoCategoriaResponse> categorias();

    Page<CatalogoProductoResponse> productos(String search, Integer idCategoria, Pageable pageable);

    CatalogoProductoResponse producto(Integer id);
}
