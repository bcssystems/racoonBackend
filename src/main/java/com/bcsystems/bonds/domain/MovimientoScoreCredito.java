package com.bcsystems.bonds.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;
import java.time.LocalDateTime;

@Audited
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "movimiento_score_credito")
public class MovimientoScoreCredito {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idMovimiento;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "id_credito")
    private Credito credito;
    @Column(nullable = false) private Integer scoreAnterior;
    @Column(nullable = false) private Integer scoreNuevo;
    @Column(nullable = false) private Integer puntosCambio;
    @Column(nullable = false, length = 300) private String motivo;
    @Column(nullable = false) private LocalDateTime fecha;
}
