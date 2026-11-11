package com.bcsystems.bonds.service.impl;

import com.bcsystems.bonds.domain.CodigoPostalCache;
import com.bcsystems.bonds.dto.CodigoPostalResponse;
import com.bcsystems.bonds.exception.InvalidEntryException;
import com.bcsystems.bonds.repository.CodigoPostalCacheRepository;
import com.bcsystems.bonds.service.CodigoPostalService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CodigoPostalServiceImpl implements CodigoPostalService {
    private final CodigoPostalCacheRepository cacheRepository;
    private final ObjectMapper objectMapper;

    @Value("${postal.cache-hours:168}") private long cacheHours;
    @Value("${postal.codigoszip-url:https://api.codigos.zip/api/zip/{cp}?pais=MX}") private String codigosZipUrl;
    @Value("${postal.codigoszip-key:}") private String codigosZipKey;
    @Value("${postal.fallback-url}") private String fallbackUrl;

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4)).build();

    @Override
    @Transactional
    public CodigoPostalResponse consultar(String cp) {
        if (cp == null || !cp.matches("\\d{5}")) {
            throw new InvalidEntryException("El código postal debe contener 5 dígitos");
        }
        Optional<CodigoPostalCache> cached = cacheRepository.findByCp(cp);
        if (cached.isPresent() && cached.get().getConsultadoEn().plusHours(cacheHours).isAfter(LocalDateTime.now())) {
            return fromCache(cached.get(), true);
        }

        List<ProviderResult> providers = new ArrayList<>();
        if (codigosZipKey != null && !codigosZipKey.isBlank()) {
            providers.add(consultarProveedor(codigosZipUrl.replace("{cp}", cp), "CODIGOS.ZIP", codigosZipKey));
        }
        providers.add(consultarProveedor(fallbackUrl.replace("{cp}", cp), "RESPALDO", null));
        for (ProviderResult result : providers) {
            if (result != null && !result.colonias().isEmpty()) {
                try {
                    String json = objectMapper.writeValueAsString(result);
                    CodigoPostalCache item = cached.orElseGet(CodigoPostalCache::new);
                    item.setCp(cp);
                    item.setDatosJson(json);
                    item.setFuente(result.fuente());
                    item.setConsultadoEn(LocalDateTime.now());
                    return fromCache(cacheRepository.save(item), false);
                } catch (Exception e) {
                    throw new InvalidEntryException("No se pudo guardar la consulta del código postal");
                }
            }
        }
        throw new InvalidEntryException("No se encontró información para el código postal " + cp + ". Captura la colonia y municipio manualmente.");
    }

    private ProviderResult consultarProveedor(String url, String fuente) {
        return consultarProveedor(url, fuente, null);
    }

    private ProviderResult consultarProveedor(String url, String fuente, String apiKey) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6)).header("Accept", "application/json");
            if (apiKey != null) builder.header("X-API-Key", apiKey);
            HttpRequest request = builder.GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) return null;
            JsonNode root = objectMapper.readTree(response.body());
            return new ProviderResult(first(root, "estado", "state", "state_name"),
                    first(root, "municipio", "municipality", "place name", "city"),
                    colonias(root), fuente);
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<String> colonias(JsonNode root) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        collect(root, values, "colonia");
        collect(root, values, "coloniaNombre");
        collect(root, values, "colonias");
        collect(root, values, "settlement");
        collect(root, values, "place name");
        JsonNode places = root.path("places");
        if (places.isArray()) places.forEach(p -> values.add(first(p, "place name", "colonia", "settlement")));
        return values.stream().filter(v -> v != null && !v.isBlank()).toList();
    }

    private void collect(JsonNode node, Set<String> values, String field) {
        if (node == null || node.isMissingNode()) return;
        if (node.isObject()) {
            JsonNode value = node.get(field);
            if (value != null && value.isTextual()) values.add(value.asText());
            if (value != null && value.isArray()) value.forEach(item -> { if (item.isValueNode()) values.add(item.asText()); });
            node.elements().forEachRemaining(child -> collect(child, values, field));
        } else if (node.isArray()) node.forEach(child -> collect(child, values, field));
    }

    private String first(JsonNode node, String... fields) {
        if (node == null) return "";
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && value.isValueNode() && !value.asText().isBlank()) return value.asText();
        }
        if (node.has("response")) return first(node.get("response"), fields);
        if (node.has("result")) return first(node.get("result"), fields);
        if (node.has("places") && node.get("places").isArray() && node.get("places").size() > 0) return first(node.get("places").get(0), fields);
        return "";
    }

    private CodigoPostalResponse fromCache(CodigoPostalCache item, boolean cache) {
        try {
            ProviderResult data = objectMapper.readValue(item.getDatosJson(), ProviderResult.class);
            return new CodigoPostalResponse(item.getCp(), data.estado(), data.municipio(), data.colonias(), item.getFuente(), item.getConsultadoEn(), cache);
        } catch (Exception e) { throw new InvalidEntryException("La información del código postal no es válida"); }
    }

    private record ProviderResult(String estado, String municipio, List<String> colonias, String fuente) {}
}
