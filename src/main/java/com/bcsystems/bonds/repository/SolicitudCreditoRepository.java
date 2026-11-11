package com.bcsystems.bonds.repository;

import com.bcsystems.bonds.domain.SolicitudCredito;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SolicitudCreditoRepository extends JpaRepository<SolicitudCredito, Integer> {
    List<SolicitudCredito> findAllByOrderByFechaSolicitudDesc();
    List<SolicitudCredito> findByClienteIdClienteOrderByFechaSolicitudDesc(Integer idCliente);
}
