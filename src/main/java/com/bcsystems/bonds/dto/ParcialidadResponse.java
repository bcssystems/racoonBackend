package com.bcsystems.bonds.dto;

import java.time.LocalDateTime;

public record ParcialidadResponse(
        Integer idParcialidad,
        Integer numero,
        LocalDateTime fechaVencimiento,
        Double capitalProgramado,
        Double interesProgramado,
        Double capitalPagado,
        Double interesPagado,
        String estado
) {}
