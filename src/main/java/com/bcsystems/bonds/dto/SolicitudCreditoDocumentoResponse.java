package com.bcsystems.bonds.dto;

import java.time.LocalDateTime;

public record SolicitudCreditoDocumentoResponse(
        Integer idDocumento, String tipo, String url, String nombreArchivo,
        String mimeType, LocalDateTime fechaCarga
) {}
