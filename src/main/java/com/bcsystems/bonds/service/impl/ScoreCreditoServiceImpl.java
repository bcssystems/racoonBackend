package com.bcsystems.bonds.service.impl;

import com.bcsystems.bonds.domain.*;
import com.bcsystems.bonds.repository.*;
import com.bcsystems.bonds.service.ScoreCreditoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ScoreCreditoServiceImpl implements ScoreCreditoService {
    private final ClienteRepository clienteRepository;
    private final CreditoRepository creditoRepository;
    private final MovimientoScoreCreditoRepository movimientoRepository;

    @Override
    @Transactional
    public int recalcular(Cliente cliente) {
        int anterior = cliente.getScoreCredito() == null ? 100 : cliente.getScoreCredito();
        int penalizacion = 0;
        int bonificacion = 0;
        LocalDateTime ahora = LocalDateTime.now();
        for (Credito credito : creditoRepository.findByClienteIdClienteOrderByFechaCreacionDesc(cliente.getIdCliente())) {
            for (ParcialidadCredito parcialidad : credito.getParcialidades()) {
                if (parcialidad.getFechaVencimiento() == null) continue;
                boolean pagada = "PAGADA".equalsIgnoreCase(parcialidad.getEstado());
                if (parcialidad.getFechaVencimiento().isBefore(ahora)) {
                    if (!pagada) {
                        long dias = Math.max(1, Duration.between(parcialidad.getFechaVencimiento(), ahora).toDays());
                        penalizacion += Math.min(25, 8 + (int) (dias / 3));
                    } else if (parcialidad.getFechaUltimoPago() != null
                            && parcialidad.getFechaUltimoPago().isAfter(parcialidad.getFechaVencimiento())) {
                        long dias = Math.max(1, Duration.between(parcialidad.getFechaVencimiento(), parcialidad.getFechaUltimoPago()).toDays());
                        penalizacion += Math.min(18, 4 + (int) (dias / 5));
                    } else {
                        bonificacion++;
                    }
                }
            }
            if (credito.getEstado() != null && "CANCELADO".equals(credito.getEstado().name())) penalizacion += 15;
        }
        int nuevo = Math.max(0, Math.min(100, 100 - penalizacion + Math.min(5, bonificacion)));
        cliente.setScoreCredito(nuevo);
        clienteRepository.save(cliente);
        if (nuevo != anterior) {
            movimientoRepository.save(MovimientoScoreCredito.builder()
                    .cliente(cliente).scoreAnterior(anterior).scoreNuevo(nuevo)
                    .puntosCambio(nuevo - anterior)
                    .motivo(nuevo < anterior ? "Retrasos o incumplimientos de parcialidades" : "Pagos puntuales registrados")
                    .fecha(ahora).build());
        }
        return nuevo;
    }
}
