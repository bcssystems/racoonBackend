package com.bcsystems.bonds.service.impl;

import com.bcsystems.bonds.repository.ClienteRepository;
import com.bcsystems.bonds.service.ScoreCreditoService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScoreCreditoScheduler {
    private final ClienteRepository clienteRepository;
    private final ScoreCreditoService scoreCreditoService;

    @Scheduled(cron = "0 10 3 * * *")
    public void actualizarScores() {
        clienteRepository.findAll().forEach(scoreCreditoService::recalcular);
    }
}
