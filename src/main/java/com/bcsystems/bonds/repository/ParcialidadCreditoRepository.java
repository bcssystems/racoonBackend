package com.bcsystems.bonds.repository;

import com.bcsystems.bonds.domain.ParcialidadCredito;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ParcialidadCreditoRepository extends JpaRepository<ParcialidadCredito, Integer> {
    List<ParcialidadCredito> findByCreditoIdCreditoOrderByNumeroAsc(Integer idCredito);
}
