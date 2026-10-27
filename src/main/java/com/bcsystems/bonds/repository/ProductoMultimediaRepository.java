package com.bcsystems.bonds.repository;

import com.bcsystems.bonds.domain.ProductoMultimedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductoMultimediaRepository extends JpaRepository<ProductoMultimedia, Integer> {
    List<ProductoMultimedia> findByProductoIdProductoOrderByOrdenAscIdMultimediaAsc(Integer idProducto);
    List<ProductoMultimedia> findByProductoIdProductoInOrderByOrdenAscIdMultimediaAsc(Collection<Integer> ids);
    Optional<ProductoMultimedia> findFirstByProductoIdProductoAndEsPrincipalTrueAndTipoOrderByOrdenAsc(
            Integer idProducto, String tipo);
    long countByProductoIdProducto(Integer idProducto);
    boolean existsByIdMultimediaAndProductoIdProducto(Integer idMultimedia, Integer idProducto);

    @Query("select coalesce(max(m.orden), -1) from ProductoMultimedia m where m.producto.idProducto = :idProducto")
    int maxOrden(@Param("idProducto") Integer idProducto);
}
