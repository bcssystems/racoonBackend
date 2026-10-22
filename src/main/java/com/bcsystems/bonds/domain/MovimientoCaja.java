package com.bcsystems.bonds.domain;

import com.bcsystems.bonds.domain.en.TipoMovimientoCaja;
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
@Table(name = "movimiento_caja")
public class MovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idMovimientoCaja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caja", nullable = false)
    private Caja caja;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoMovimientoCaja tipo;

    @Column(nullable = false)
    private Double monto;

    @Column(nullable = false, length = 300)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private Persona usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_abono")
    private Abono abono;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_venta")
    private Venta venta;

    @Column(updatable = false)
    private LocalDateTime fecha;

    @Builder.Default
    @Column(nullable = false)
    private Boolean cancelado = false;

    private LocalDateTime fechaCancelacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario_cancelacion")
    private Persona usuarioCancelacion;

    @Column(length = 300)
    private String motivoCancelacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_solicitante_cancelacion")
    private Persona solicitanteCancelacion;

    private LocalDateTime fechaSolicitudCancelacion;

    @Column(length = 4)
    private String codigoAutorizacion;

    private LocalDateTime fechaGeneracionCodigo;

    private String generoAutorizacion;
}
