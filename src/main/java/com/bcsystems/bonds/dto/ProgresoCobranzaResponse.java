package com.bcsystems.bonds.dto;

import java.time.LocalDate;
import java.util.List;

public record ProgresoCobranzaResponse(
        LocalDate fecha,
        Double metaTotal,
        Double cobradoTotal,
        Double porcentajeTotal,
        List<ProgresoCobradorResponse> cobradores
) {}
