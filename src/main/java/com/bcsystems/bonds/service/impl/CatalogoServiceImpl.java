package com.bcsystems.bonds.service.impl;

import com.bcsystems.bonds.domain.Producto;
import com.bcsystems.bonds.domain.ProductoMultimedia;
import com.bcsystems.bonds.dto.CatalogoCategoriaResponse;
import com.bcsystems.bonds.dto.CatalogoMultimediaResponse;
import com.bcsystems.bonds.dto.CatalogoProductoResponse;
import com.bcsystems.bonds.exception.NotFoundException;
import com.bcsystems.bonds.repository.CategoriaRepository;
import com.bcsystems.bonds.repository.ProductoMultimediaRepository;
import com.bcsystems.bonds.repository.ProductoRepository;
import com.bcsystems.bonds.service.CatalogoService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CatalogoServiceImpl implements CatalogoService {
    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;
    private final ProductoMultimediaRepository multimediaRepository;

    public CatalogoServiceImpl(CategoriaRepository categoriaRepository,
                               ProductoRepository productoRepository,
                               ProductoMultimediaRepository multimediaRepository) {
        this.categoriaRepository = categoriaRepository;
        this.productoRepository = productoRepository;
        this.multimediaRepository = multimediaRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogoCategoriaResponse> categorias() {
        return categoriaRepository.findAllActivas().stream()
                .map(categoria -> new CatalogoCategoriaResponse(categoria.getIdCategoria(), categoria.getNombre()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CatalogoProductoResponse> productos(String search, Integer idCategoria, Pageable pageable) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        Page<Producto> page = productoRepository.buscarParaCatalogo(normalizedSearch, idCategoria, pageable);
        List<Integer> ids = page.getContent().stream().map(Producto::getIdProducto).toList();
        Map<Integer, List<ProductoMultimedia>> mediaPorProducto = ids.isEmpty() ? Map.of()
                : multimediaRepository.findByProductoIdProductoInOrderByOrdenAscIdMultimediaAsc(ids).stream()
                .collect(Collectors.groupingBy(media -> media.getProducto().getIdProducto()));
        return page.map(producto -> toResponse(producto, mediaPorProducto.getOrDefault(producto.getIdProducto(), List.of()), false));
    }

    @Override
    @Transactional(readOnly = true)
    public CatalogoProductoResponse producto(Integer id) {
        Producto producto = productoRepository.findById(id)
                .filter(item -> Boolean.TRUE.equals(item.getActivo()))
                .filter(item -> item.getCategoria() == null || Boolean.TRUE.equals(item.getCategoria().getActivo()))
                .orElseThrow(() -> new NotFoundException("Producto no encontrado"));
        return toResponse(producto,
                multimediaRepository.findByProductoIdProductoOrderByOrdenAscIdMultimediaAsc(id), true);
    }

    private CatalogoProductoResponse toResponse(Producto producto, List<ProductoMultimedia> media, boolean includeMedia) {
        List<ProductoMultimedia> images = media.stream()
                .filter(item -> "IMAGEN".equals(item.getTipo()))
                .sorted(Comparator.comparing((ProductoMultimedia item) -> !Boolean.TRUE.equals(item.getEsPrincipal()))
                        .thenComparing(ProductoMultimedia::getOrden, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProductoMultimedia::getIdMultimedia))
                .toList();
        String principal = images.stream().map(ProductoMultimedia::getUrl).findFirst().orElse(null);
        List<CatalogoMultimediaResponse> multimedia = includeMedia
                ? media.stream().map(item -> new CatalogoMultimediaResponse(
                        item.getIdMultimedia(), item.getTipo(), item.getUrl(), item.getNombreArchivo(),
                        item.getEsPrincipal(), item.getOrden())).toList()
                : List.of();
        return new CatalogoProductoResponse(producto.getIdProducto(), producto.getSku(), producto.getNombre(),
                producto.getDescripcion(), producto.getPrecioBase(), producto.getStockActual(),
                producto.getCategoria() != null ? producto.getCategoria().getNombre() : null,
                principal, multimedia);
    }
}
