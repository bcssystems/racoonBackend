package com.bcsystems.bonds.repository;

import com.bcsystems.bonds.domain.Abono;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AbonoRepository extends JpaRepository<Abono, Integer> {

    List<Abono> findByCreditoIdCreditoOrderByFechaDesc(Integer idCredito);

    List<Abono> findByCajaIdCajaAndFechaBetweenOrderByFechaDesc(Integer idCaja, LocalDateTime inicio, LocalDateTime fin);

    @Query("select a from Abono a join fetch a.credito c left join fetch c.cobrador " +
            "where a.fecha >= :desde and a.fecha < :hasta and a.cancelado = false")
    List<Abono> findAbonosVigentesEntre(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    Optional<Abono> findFirstByCreditoClienteIdClienteOrderByFechaDesc(Integer idCliente);

    @Query("SELECT MAX(a.fecha) FROM Abono a WHERE a.credito.cliente.idCliente = :idCliente")
    Optional<LocalDateTime> findMaxFechaByCliente(@Param("idCliente") Integer idCliente);
}
