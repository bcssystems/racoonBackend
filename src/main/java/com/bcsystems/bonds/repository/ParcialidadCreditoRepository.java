package com.bcsystems.bonds.repository;

import com.bcsystems.bonds.domain.ParcialidadCredito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParcialidadCreditoRepository extends JpaRepository<ParcialidadCredito, Integer> {
    List<ParcialidadCredito> findByCreditoIdCreditoOrderByNumeroAsc(Integer idCredito);

    @Query("select p from ParcialidadCredito p join fetch p.credito c join fetch c.cliente cl " +
            "where p.fechaVencimiento >= :desde and p.fechaVencimiento < :hasta " +
            "and (:idCobrador is null or c.cobrador.idPersona = :idCobrador) " +
            "and (:estado is null or p.estado = :estado) order by p.fechaVencimiento asc, p.numero asc")
    List<ParcialidadCredito> buscarAgenda(@Param("idCobrador") Integer idCobrador,
                                          @Param("desde") LocalDateTime desde,
                                          @Param("hasta") LocalDateTime hasta,
                                          @Param("estado") String estado);
}
