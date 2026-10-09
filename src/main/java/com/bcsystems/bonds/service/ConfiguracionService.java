package com.bcsystems.bonds.service;

import com.bcsystems.bonds.dto.ConfiguracionRequest;
import com.bcsystems.bonds.dto.ConfiguracionResponse;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface ConfiguracionService {
    List<ConfiguracionResponse> listar();
    ConfiguracionResponse obtener(String clave);
    String getValor(String clave, String valorDefault);
    int getValorInt(String clave, int valorDefault);
    double getValorDouble(String clave, double valorDefault);
    ConfiguracionResponse actualizar(String clave, ConfiguracionRequest request);
    ConfiguracionResponse actualizarLogo(MultipartFile archivo);
}
