package com.bcsystems.bonds.auth;

import com.bcsystems.bonds.domain.Persona;
import com.bcsystems.bonds.domain.Rol;
import com.bcsystems.bonds.domain.Token;
import com.bcsystems.bonds.exception.InvalidEntryException;
import com.bcsystems.bonds.repository.PersonaRepository;
import com.bcsystems.bonds.repository.RolRepository;
import com.bcsystems.bonds.repository.TokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private final PersonaRepository personaRepository;
    private final TokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RolRepository rolRepository;

    public AuthService(PersonaRepository personaRepository, TokenRepository tokenRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuthenticationManager authenticationManager, RolRepository rolRepository) {
        this.personaRepository = personaRepository; this.tokenRepository = tokenRepository; this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService; this.authenticationManager = authenticationManager; this.rolRepository = rolRepository;
    }

    @Transactional
    public AuthSession register(RegisterRequest request) {
        if (personaRepository.existsByUsuarioIgnoreCase(request.usuario())) throw new InvalidEntryException("El usuario ya existe");
        if (personaRepository.count() > 0 && !tienePermiso("PERSONAS_CREAR")) throw new InvalidEntryException("Registro no autorizado");
        Rol rol = rolRepository.findById(request.idRol()).orElseThrow(() -> new InvalidEntryException("Rol no encontrado"));
        if (!Boolean.TRUE.equals(rol.getActivo())) throw new InvalidEntryException("El rol seleccionado está inactivo");
        Persona persona = Persona.builder().nombre(request.nombre()).apellido(request.apellido()).usuario(request.usuario())
                .password(passwordEncoder.encode(request.password())).rol(rol).activa(true).build();
        persona = personaRepository.save(persona);
        return prepare(jwtService.generateToken(persona), persona);
    }

    @Transactional
    public AuthSession login(LoginRequest request) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.usuario(), request.password()));
        Persona persona = personaRepository.findByUsuarioIgnoreCase(request.usuario()).orElseThrow(() -> new InvalidEntryException("Credenciales inválidas"));
        revokeAllTokens(persona);
        return prepare(jwtService.generateToken(persona), persona);
    }

    @Transactional
    public AuthSession refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) throw new InvalidEntryException("Token de refresco inválido");
        final String username;
        try {
            username = jwtService.extractUsername(refreshToken);
            if (!jwtService.belongsToRacoon(refreshToken) || !"refresh".equals(jwtService.tokenType(refreshToken))) throw new InvalidEntryException("Token de refresco inválido");
        } catch (Exception e) { throw new InvalidEntryException("Token de refresco inválido"); }
        Persona persona = personaRepository.findByUsuarioIgnoreCase(username).orElseThrow(() -> new InvalidEntryException("Usuario no encontrado"));
        if (!Boolean.TRUE.equals(persona.getActiva())) throw new InvalidEntryException("Usuario inactivo");
        Token stored = tokenRepository.findByRefreshTokenHash(jwtService.hashToken(refreshToken)).orElse(null);
        if (stored == null || Boolean.TRUE.equals(stored.getIsRevoked()) || Boolean.TRUE.equals(stored.getIsExpired())) {
            if (stored != null && stored.getSessionFamily() != null) tokenRepository.revokeAllBySessionFamily(stored.getSessionFamily());
            throw new InvalidEntryException("Token de refresco inválido o reutilizado");
        }
        if (jwtService.isTokenExpired(refreshToken)) { stored.setIsExpired(true); tokenRepository.save(stored); throw new InvalidEntryException("Token de refresco expirado"); }
        if (tokenRepository.consumeRefreshToken(stored.getIdToken()) == 0) throw new InvalidEntryException("Token de refresco reutilizado");
        revokeAllTokens(persona);
        return prepare(jwtService.generateToken(persona, stored.getSessionFamily()), persona);
    }

    @Transactional
    public void logout(String accessToken, String refreshToken) {
        Token stored = refreshToken == null ? null : tokenRepository.findByRefreshTokenHash(jwtService.hashToken(refreshToken)).orElse(null);
        if (stored == null && accessToken != null) stored = tokenRepository.findByTokenHash(jwtService.hashToken(accessToken)).orElse(null);
        if (stored != null) { stored.setIsRevoked(true); stored.setIsExpired(true); tokenRepository.save(stored); }
    }

    public AuthResponse session(String username) {
        Persona persona = personaRepository.findByUsuarioIgnoreCase(username).orElseThrow(() -> new InvalidEntryException("Usuario no encontrado"));
        if (!Boolean.TRUE.equals(persona.getActiva())) throw new InvalidEntryException("Usuario inactivo");
        return buildResponse(persona);
    }

    private AuthSession prepare(Token token, Persona persona) {
        String access = token.getToken(), refresh = token.getRefreshToken();
        token.setTokenHash(jwtService.hashToken(access)); token.setRefreshTokenHash(jwtService.hashToken(refresh));
        token.setToken(null); token.setRefreshToken(null); tokenRepository.save(token);
        return new AuthSession(buildResponse(persona), access, refresh);
    }

    private void revokeAllTokens(Persona persona) {
        var tokens = tokenRepository.findAllValidTokensByPersona(persona.getIdPersona());
        tokens.forEach(t -> { t.setIsExpired(true); t.setIsRevoked(true); });
        tokenRepository.saveAll(tokens);
    }

    private boolean tienePermiso(String permiso) { Authentication auth = SecurityContextHolder.getContext().getAuthentication(); return auth != null && auth.getAuthorities().stream().anyMatch(a -> permiso.equals(a.getAuthority())); }
    private AuthResponse buildResponse(Persona persona) {
        var permisos = persona.getRol().getPermisos().stream().map(p -> p.getClave()).collect(Collectors.toSet());
        if (persona.getPermisosAdicionales() != null) persona.getPermisosAdicionales().forEach(p -> permisos.add(p.getPermiso().getClave()));
        return new AuthResponse(persona.getUsuario(), persona.getNombre() + " " + persona.getApellido(), persona.getRol().getNombre(), permisos.stream().sorted().toList());
    }
}
