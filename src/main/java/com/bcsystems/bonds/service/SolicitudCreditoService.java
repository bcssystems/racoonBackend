package com.bcsystems.bonds.service;

import com.bcsystems.bonds.dto.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import java.util.List;

public interface SolicitudCreditoService {
    SolicitudCreditoResponse crear(SolicitudCreditoRequest request);
    List<SolicitudCreditoResponse> listar(String estado, Integer idCliente);
    SolicitudCreditoResponse obtener(Integer id);
    SolicitudCreditoDocumentoResponse agregarDocumento(Integer id, String tipo, MultipartFile archivo);
    Resource descargarDocumento(Integer idSolicitud, Integer idDocumento);
    SolicitudCreditoResponse resolver(Integer id, SolicitudCreditoResolverRequest request);
}
