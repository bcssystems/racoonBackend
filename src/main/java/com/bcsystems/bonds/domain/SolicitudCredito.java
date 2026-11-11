package com.bcsystems.bonds.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Table(name = "solicitud_credito")
public class SolicitudCredito {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idSolicitud;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_solicitante", nullable = false)
    private Persona solicitante;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_resolutor")
    private Persona resolutor;
    @Column(nullable = false, length = 20) @Builder.Default
    private String estado = "PENDIENTE";
    @Column(nullable = false) private Double montoSolicitado;
    private Double montoSugerido;
    private Double montoAprobado;
    private Integer parcialidadesSolicitadas;
    private Integer parcialidadesAprobadas;
    @Lob @Column(columnDefinition = "TEXT") private String encuestaJson;
    @Column(length = 1000) private String observaciones;
    @Column(nullable = false) @Builder.Default private LocalDateTime fechaSolicitud = LocalDateTime.now();
    private LocalDateTime fechaResolucion;
}
