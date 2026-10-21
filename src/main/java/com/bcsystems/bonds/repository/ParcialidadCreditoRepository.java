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
            "where p.fechaVencimiento < :hasta " +
            "and (p.fechaVencimiento >= :desde or (p.fechaVencimiento < :desde and p.estado <> 'PAGADA')) " +
            "and (:idCobrador is null or c.cobrador.idPersona = :idCobrador) " +
            "and (:estado is null or p.estado = :estado) " +
            "and (:cliente is null or lower(concat(cl.nombre, ' ', coalesce(cl.apellidoPaterno, ''))) like lower(concat('%', :cliente, '%'))) " +
            "order by p.fechaVencimiento asc, p.numero asc")
    List<ParcialidadCredito> buscarAgenda(@Param("idCobrador") Integer idCobrador,
                                          @Param("desde") LocalDateTime desde,
                                          @Param("hasta") LocalDateTime hasta,
                                           @Param("estado") String estado,
                                            @Param("cliente") String cliente);

    @Query("select p from ParcialidadCredito p join fetch p.credito c join fetch c.cliente cl " +
            "where c.cobrador.idPersona = :idCobrador and p.estado <> 'PAGADA' " +
            "and p.fechaVencimiento < :limite order by p.fechaVencimiento asc, p.numero asc")
    List<ParcialidadCredito> findPendientesParaItinerario(@Param("idCobrador") Integer idCobrador,
                                                            @Param("limite") LocalDateTime limite);
}
