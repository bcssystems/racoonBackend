package com.bcsystems.bonds.repository;

import com.bcsystems.bonds.domain.MensajeWhatsApp;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;

public interface MensajeWhatsAppRepository extends JpaRepository<MensajeWhatsApp, Long> {
    boolean existsByTipoAndReferenciaAndEstadoIn(String tipo, String referencia, java.util.Collection<String> estados);
    Optional<MensajeWhatsApp> findFirstByEstadoAndFechaProgramadaLessThanEqualOrderByFechaProgramadaAsc(
            String estado, LocalDateTime fecha);
}
