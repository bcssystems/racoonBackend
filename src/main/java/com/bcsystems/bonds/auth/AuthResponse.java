package com.bcsystems.bonds.auth;

import java.util.List;

public record AuthResponse(
        String usuario,
        String nombre,
        String rol,
        List<String> permisos
) {}
