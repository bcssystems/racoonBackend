package com.bcsystems.bonds.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.public-dir:./uploads/multimedia}")
    private String publicUploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(publicUploadDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/uploads/multimedia/**")
                .addResourceLocations("file:" + uploadPath.toString() + "/");
    }
}
