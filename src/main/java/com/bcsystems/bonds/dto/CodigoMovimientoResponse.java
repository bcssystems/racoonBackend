package com.bcsystems.bonds.dto;

import java.time.LocalDateTime;

public record CodigoMovimientoResponse(Integer idMovimiento, String codigo,
                                       LocalDateTime generadoEn, LocalDateTime expiraEn) {}
