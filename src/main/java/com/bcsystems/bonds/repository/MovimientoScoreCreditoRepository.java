package com.bcsystems.bonds.repository;

import com.bcsystems.bonds.domain.MovimientoScoreCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MovimientoScoreCreditoRepository extends JpaRepository<MovimientoScoreCredito, Integer> {
    List<MovimientoScoreCredito> findByClienteIdClienteOrderByFechaDesc(Integer idCliente);
}
