package com.bcsystems.bonds.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "mensaje_whatsapp")
public class MensajeWhatsApp {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idMensaje;
    @Column(length = 30, nullable = false) private String tipo;
    @Column(length = 80) private String referencia;
    @Column(length = 30) private String destinatario;
    @Column(columnDefinition = "TEXT", nullable = false) private String mensaje;
    @Column(length = 20, nullable = false) private String estado;
    @Column(columnDefinition = "TEXT") private String detalleError;
    @Builder.Default @Column(nullable = false) private Integer intentos = 0;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaEnvio;
}
