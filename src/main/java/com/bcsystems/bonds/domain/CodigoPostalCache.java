package com.bcsystems.bonds.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Table(name = "codigo_postal_cache", indexes = @Index(name = "idx_cp_cache_cp", columnList = "cp", unique = true))
public class CodigoPostalCache {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "cp", nullable = false, length = 5, unique = true)
    private String cp;

    @Lob @Column(nullable = false, columnDefinition = "TEXT")
    private String datosJson;

    @Column(length = 80)
    private String fuente;

    @Column(nullable = false)
    private LocalDateTime consultadoEn;
}
