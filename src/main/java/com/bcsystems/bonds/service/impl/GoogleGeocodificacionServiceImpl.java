package com.bcsystems.bonds.service.impl;

import com.bcsystems.bonds.domain.Cliente;
import com.bcsystems.bonds.dto.ClienteUbicacionRequest;
import com.bcsystems.bonds.dto.ClienteUbicacionResponse;
import com.bcsystems.bonds.exception.InvalidEntryException;
import com.bcsystems.bonds.exception.NotFoundException;
import com.bcsystems.bonds.repository.ClienteRepository;
import com.bcsystems.bonds.service.AuditoriaService;
import com.bcsystems.bonds.service.GeocodificacionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GoogleGeocodificacionServiceImpl implements GeocodificacionService {
    private final ClienteRepository clienteRepository;
    private final AuditoriaService auditoriaService;
    private final ObjectMapper objectMapper;
    @Value("${google.maps.server-api-key:}") private String apiKey;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Override @Transactional
    public ClienteUbicacionResponse geocodificar(Integer idCliente) {
        Cliente cliente = find(idCliente);
        if (apiKey == null || apiKey.isBlank()) throw new InvalidEntryException("Google Maps no está configurado en el servidor");
        String direccion = construirDireccion(cliente);
        if (direccion.isBlank()) throw new InvalidEntryException("El cliente no tiene una dirección suficiente para localizarlo");
        try {
            String url = "https://maps.googleapis.com/maps/api/geocode/json?address=" + URLEncoder.encode(direccion, StandardCharsets.UTF_8) + "&language=es&region=mx&key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("Accept", "application/json").GET().build();
            JsonNode root = objectMapper.readTree(client.send(request, HttpResponse.BodyHandlers.ofString()).body());
            if (!"OK".equals(root.path("status").asText()) || !root.path("results").isArray() || root.path("results").isEmpty()) {
                cliente.setEstadoGeocodificacion("NO_ENCONTRADO");
                cliente.setGeocodificadoEn(LocalDateTime.now());
                clienteRepository.save(cliente);
                throw new InvalidEntryException("Google Maps no encontró la dirección");
            }
            JsonNode result = root.path("results").get(0);
            JsonNode location = result.path("geometry").path("location");
            cliente.setLatitud(location.path("lat").asDouble());
            cliente.setLongitud(location.path("lng").asDouble());
            cliente.setDireccionNormalizada(result.path("formatted_address").asText(direccion));
            cliente.setPrecisionUbicacion(result.path("geometry").path("location_type").asText("APPROXIMATE"));
            cliente.setEstadoGeocodificacion("COMPLETADO");
            cliente.setGeocodificadoEn(LocalDateTime.now());
            clienteRepository.save(cliente);
            auditoriaService.registrar("Cliente", idCliente, "UBICACION", usuario(), "Ubicación geocodificada con Google Maps");
            return response(cliente);
        } catch (InvalidEntryException e) { throw e; }
        catch (Exception e) { cliente.setEstadoGeocodificacion("ERROR"); cliente.setGeocodificadoEn(LocalDateTime.now()); clienteRepository.save(cliente); throw new InvalidEntryException("No fue posible consultar Google Maps"); }
    }

    @Override @Transactional
    public ClienteUbicacionResponse actualizar(Integer idCliente, ClienteUbicacionRequest request) {
        if (request.latitud() == null || request.longitud() == null || request.latitud() < -90 || request.latitud() > 90 || request.longitud() < -180 || request.longitud() > 180) throw new InvalidEntryException("Coordenadas inválidas");
        Cliente cliente = find(idCliente);
        cliente.setLatitud(request.latitud()); cliente.setLongitud(request.longitud());
        cliente.setPrecisionUbicacion(request.precisionUbicacion() == null ? "MANUAL" : request.precisionUbicacion());
        cliente.setEstadoGeocodificacion("COMPLETADO"); cliente.setGeocodificadoEn(LocalDateTime.now());
        clienteRepository.save(cliente);
        auditoriaService.registrar("Cliente", idCliente, "UBICACION", usuario(), "Ubicación actualizada manualmente");
        return response(cliente);
    }

    private Cliente find(Integer id) { return clienteRepository.findById(id).orElseThrow(() -> new NotFoundException("Cliente no encontrado")); }
    private String construirDireccion(Cliente c) { return String.join(", ", java.util.stream.Stream.of(c.getCalle(), c.getNumExt(), c.getColonia(), c.getMunicipio(), c.getEstado(), c.getCp(), "México").filter(x -> x != null && !x.isBlank()).toList()); }
    private ClienteUbicacionResponse response(Cliente c) { return new ClienteUbicacionResponse(c.getIdCliente(), c.getLatitud(), c.getLongitud(), c.getDireccionNormalizada(), c.getPrecisionUbicacion(), c.getEstadoGeocodificacion(), c.getGeocodificadoEn()); }
    private String usuario() { var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication(); return auth == null ? "SISTEMA" : auth.getName(); }
}
