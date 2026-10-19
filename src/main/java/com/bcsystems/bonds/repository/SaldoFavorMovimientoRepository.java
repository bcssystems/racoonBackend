package com.bcsystems.bonds.repository;

import com.bcsystems.bonds.domain.SaldoFavorMovimiento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SaldoFavorMovimientoRepository extends JpaRepository<SaldoFavorMovimiento, Integer> {
    List<SaldoFavorMovimiento> findByClienteIdClienteOrderByFechaDesc(Integer idCliente);
}
