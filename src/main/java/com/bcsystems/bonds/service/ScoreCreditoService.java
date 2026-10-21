package com.bcsystems.bonds.service;

import com.bcsystems.bonds.domain.Cliente;

public interface ScoreCreditoService {
    int recalcular(Cliente cliente);
}
