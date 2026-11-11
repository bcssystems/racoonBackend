package com.bcsystems.bonds.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Table(name = "solicitud_credito_documento")
public class SolicitudCreditoDocumento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idDocumento;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_solicitud", nullable = false)
    private SolicitudCredito solicitud;
    @Column(nullable = false, length = 30) private String tipo;
    @Column(nullable = false, length = 500) private String url;
    @Column(length = 255) private String nombreArchivo;
    @Column(length = 100) private String mimeType;
    @Column(nullable = false) @Builder.Default private LocalDateTime fechaCarga = LocalDateTime.now();
}
