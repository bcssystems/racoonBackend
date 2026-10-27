package com.bcsystems.bonds.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

@Entity
@Audited
@Getter @Setter @Builder
@AllArgsConstructor @NoArgsConstructor
@Table(name = "producto_multimedia", indexes = {
        @Index(name = "idx_producto_multimedia_producto_orden", columnList = "id_producto, orden")
})
public class ProductoMultimedia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idMultimedia;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false, length = 500)
    private String url;

    @Column(length = 255)
    private String nombreArchivo;

    @Column(nullable = false)
    @Builder.Default
    private Boolean esPrincipal = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer orden = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaSubida;
}
