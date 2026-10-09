package com.bcsystems.bonds.domain;

import com.bcsystems.bonds.domain.en.TipoMovimiento;
import com.bcsystems.bonds.domain.en.EstadoMovimientoStock;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.envers.Audited;

import java.time.LocalDateTime;

@Audited
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "movimiento_stock")
public class MovimientoStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer idMovimiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_producto", nullable = false)
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_sucursal")
    private Sucursal sucursal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoMovimiento tipoMovimiento;

    @Column(nullable = false)
    private Integer cantidad;

    @Column
    private Integer stockAnterior;

    @Column
    private Integer stockNuevo;

    @Column(length = 50)
    private String referencia;

    @Column(length = 100)
    private String usuario;

    @Column(columnDefinition = "TEXT")
    private String observacion;

    @Column
    private Double costoUnitario;

    @Column
    private Double costoTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EstadoMovimientoStock estado = EstadoMovimientoStock.APROBADO;

    @Column(length = 4)
    private String codigoAutorizacion;
    private LocalDateTime fechaGeneracionCodigo;
    @Column(length = 50)
    private String generoAutorizacion;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime fechaMovimiento;
}
