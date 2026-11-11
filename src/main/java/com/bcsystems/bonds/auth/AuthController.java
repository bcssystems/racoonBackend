package com.bcsystems.bonds.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final boolean secureCookies;
    private final String sameSite;
    private final LoginRateLimiter loginRateLimiter;
    private final long accessExpiration;
    private final long refreshExpiration;

    public AuthController(AuthService authService,
                          @Value("${app.security.cookies.secure:false}") boolean secureCookies,
                          @Value("${app.security.cookies.same-site:Lax}") String sameSite,
                          LoginRateLimiter loginRateLimiter,
                          @Value("${application.security.jwt.expiration}") long accessExpiration,
                          @Value("${application.security.jwt.refresh-token.expiration}") long refreshExpiration) {
        this.authService = authService; this.secureCookies = secureCookies; this.sameSite = sameSite; this.loginRateLimiter = loginRateLimiter;
        this.accessExpiration = accessExpiration; this.refreshExpiration = refreshExpiration;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response, HttpServletRequest httpRequest) {
        String key = httpRequest.getRemoteAddr() + ":" + request.usuario().trim().toLowerCase();
        if (!loginRateLimiter.isAllowed(key)) return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build();
        try { AuthSession session = authService.login(request); loginRateLimiter.clear(key); return authenticated(session, response); }
        catch (RuntimeException e) { loginRateLimiter.recordFailure(key); throw e; }
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletResponse response) {
        return authenticated(authService.register(request), response);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthResponse> refreshToken(@CookieValue(value = "RACOON_REFRESH", required = false) String refreshToken, HttpServletResponse response) {
        return authenticated(authService.refreshToken(refreshToken), response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CookieValue(value = "RACOON_ACCESS", required = false) String accessToken,
                                       @CookieValue(value = "RACOON_REFRESH", required = false) String refreshToken,
                                       HttpServletResponse response) {
        authService.logout(accessToken, refreshToken); deleteCookies(response); return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(authService.session(userDetails.getUsername()));
    }

    @GetMapping("/csrf")
    public ResponseEntity<String> csrf(CsrfToken token) { return ResponseEntity.ok("{\"token\":\"" + token.getToken() + "\"}"); }

    private ResponseEntity<AuthResponse> authenticated(AuthSession session, HttpServletResponse response) {
        addCookie(response, "RACOON_ACCESS", session.accessToken(), "/", seconds(accessExpiration));
        addCookie(response, "RACOON_REFRESH", session.refreshToken(), "/api/v1/auth", seconds(refreshExpiration));
        return ResponseEntity.ok(session.response());
    }

    private long seconds(long milliseconds) { return Math.max(1, milliseconds / 1000); }

    private void addCookie(HttpServletResponse response, String name, String value, String path, long maxAge) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value).httpOnly(true).secure(secureCookies).sameSite(sameSite).path(path).maxAge(maxAge).build().toString());
    }

    private void deleteCookies(HttpServletResponse response) {
        addCookie(response, "RACOON_ACCESS", "", "/", 0);
        addCookie(response, "RACOON_REFRESH", "", "/api/v1/auth", 0);
    }
}
