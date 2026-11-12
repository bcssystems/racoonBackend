package com.bcsystems.bonds.auth;

import com.bcsystems.bonds.domain.Persona;
import com.bcsystems.bonds.domain.Permiso;
import com.bcsystems.bonds.domain.Token;
import com.bcsystems.bonds.domain.en.TokenType;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JwtService {
    private static final String ISSUER = "RACOON";
    private static final String AUDIENCE = "RACOON_API";

    @Value("${application.security.jwt.secret-key}") private String secretKey;
    @Value("${application.security.jwt.expiration}") private long jwtExpiration;
    @Value("${application.security.jwt.refresh-token.expiration}") private long refreshExpiration;

    public String extractUsername(String token) { return extractClaim(token, Claims::getSubject); }
    public <T> T extractClaim(String token, Function<Claims, T> resolver) { return resolver.apply(extractAllClaims(token)); }

    public Token generateToken(Persona persona) { return generateToken(persona, UUID.randomUUID().toString()); }

    public Token generateToken(Persona persona, String sessionFamily) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("name", persona.getNombre() + " " + persona.getApellido());
        claims.put("rol", persona.getRol().getNombre());
        claims.put("permisos", new ArrayList<>(getAllPermissions(persona)));
        claims.put("session_family", sessionFamily);
        String access = buildToken(claims, persona.getUsuario(), jwtExpiration, "access");
        String refresh = buildToken(Map.of("session_family", sessionFamily), persona.getUsuario(), refreshExpiration, "refresh");
        return Token.builder().token(access).refreshToken(refresh).type(TokenType.BEARER)
                .isRevoked(false).isExpired(false).sessionFamily(sessionFamily).persona(persona).build();
    }

    public String hashToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception e) { throw new IllegalStateException("SHA-256 no está disponible", e); }
    }

    public String tokenType(String token) { return extractClaim(token, c -> c.get("token_type", String.class)); }
    public String sessionFamily(String token) { return extractClaim(token, c -> c.get("session_family", String.class)); }
    public boolean belongsToRacoon(String token) {
        return ISSUER.equals(extractClaim(token, Claims::getIssuer))
                && AUDIENCE.equals(extractClaim(token, Claims::getAudience));
    }
    public boolean isTokenValid(String token, Persona persona) { return extractUsername(token).equalsIgnoreCase(persona.getUsuario()) && !isTokenExpired(token); }
    public boolean isTokenExpired(String token) { return extractClaim(token, Claims::getExpiration).before(new Date()); }

    private Set<String> getAllPermissions(Persona persona) {
        Set<String> permissions = persona.getRol().getPermisos().stream().map(Permiso::getClave).collect(Collectors.toSet());
        if (persona.getPermisosAdicionales() != null) persona.getPermisosAdicionales().forEach(p -> permissions.add(p.getPermiso().getClave()));
        return permissions;
    }

    private String buildToken(Map<String, Object> claims, String username, long expiration, String type) {
        return Jwts.builder().claims(claims).issuer(ISSUER).audience().add(AUDIENCE).and()
                .claim("token_type", type).subject(username).issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration)).signWith(getSignInKey()).compact();
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser().verifyWith(getSignInKey()).requireIssuer(ISSUER).requireAudience(AUDIENCE)
                .build().parseSignedClaims(token).getPayload();
    }

    private SecretKey getSignInKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
    }
}
