package com.bcsystems.bonds.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class AppConfig {

    @Value("${app.upload.public-dir:./uploads/multimedia}")
    private String publicUploadDir;

    @Value("${app.upload.private-dir:./uploads/privados}")
    private String privateUploadDir;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(publicUploadDir));
            Files.createDirectories(Paths.get(privateUploadDir));
        } catch (IOException e) {
            throw new RuntimeException("No se pudieron crear los directorios de uploads", e);
        }
    }
}
