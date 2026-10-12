package com.bcsystems.bonds.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

@Entity
@Audited
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "credito_parcialidad")
public class ParcialidadCredito {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idParcialidad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_credito", nullable = false)
    private Credito credito;

    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false)
    private LocalDateTime fechaVencimiento;

    @Column(nullable = false)
    private Double capitalProgramado;

    @Column(nullable = false)
    private Double interesProgramado;

    @Builder.Default
    @Column(nullable = false)
    private Double capitalPagado = 0.0;

    @Builder.Default
    @Column(nullable = false)
    private Double interesPagado = 0.0;

    @Builder.Default
    @Column(nullable = false, length = 15)
    private String estado = "PENDIENTE";
}
