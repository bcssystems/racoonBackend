package com.bcsystems.bonds.repository;

import com.bcsystems.bonds.domain.SolicitudCreditoDocumento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SolicitudCreditoDocumentoRepository extends JpaRepository<SolicitudCreditoDocumento, Integer> {
    List<SolicitudCreditoDocumento> findBySolicitudIdSolicitudOrderByFechaCargaAsc(Integer idSolicitud);
}
