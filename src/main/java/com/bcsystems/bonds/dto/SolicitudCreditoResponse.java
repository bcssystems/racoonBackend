package com.bcsystems.bonds.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SolicitudCreditoResponse(
        Integer idSolicitud, Integer idCliente, String cliente, String estado,
        Double montoSolicitado, Double montoSugerido, Double montoAprobado,
        String encuestaJson, String observaciones, String solicitante,
        String resolutor, LocalDateTime fechaSolicitud, LocalDateTime fechaResolucion,
        List<SolicitudCreditoDocumentoResponse> documentos
) {}
