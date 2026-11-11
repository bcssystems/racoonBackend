package com.bcsystems.bonds.auth;

public record AuthSession(AuthResponse response, String accessToken, String refreshToken) {}
