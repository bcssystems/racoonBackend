package com.bcsystems.bonds.repository;

import com.bcsystems.bonds.domain.MovimientoCaja;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.bcsystems.bonds.dto.CorteMovimientoDto;

public interface MovimientoCajaRepository extends JpaRepository<MovimientoCaja, Integer> {
    List<MovimientoCaja> findByCajaIdCajaOrderByFechaDesc(Integer idCaja);
    List<MovimientoCaja> findByCajaIdCajaAndFechaBetweenOrderByFechaDesc(
            Integer idCaja, LocalDateTime inicio, LocalDateTime fin);

    List<MovimientoCaja> findByVentaIdVenta(Integer idVenta);

    List<MovimientoCaja> findByFechaSolicitudCancelacionIsNotNullAndCanceladoFalseOrderByFechaSolicitudCancelacionDesc();

    @Query("select new com.bcsystems.bonds.dto.CorteMovimientoDto(" +
            "m.idMovimientoCaja, m.fecha, case when m.venta is not null then 'VENTA' when m.abono is not null then 'ABONO' " +
            "when m.tipo = com.bcsystems.bonds.domain.en.TipoMovimientoCaja.INGRESO then 'INGRESO' else 'EGRESO' end, m.motivo, m.monto, m.usuario.usuario, " +
            "case when m.abono is not null and m.abono.credito.cliente is not null then concat(m.abono.credito.cliente.nombre, ' ', coalesce(m.abono.credito.cliente.apellidoPaterno, '')) " +
            "when m.venta is not null and m.venta.cliente is not null then concat(m.venta.cliente.nombre, ' ', coalesce(m.venta.cliente.apellidoPaterno, '')) else null end, " +
            "case when m.abono is not null then m.abono.credito.folio else null end, " +
            "case when m.venta is not null then m.venta.idVenta else null end, m.cancelado, m.motivoCancelacion) " +
            "from MovimientoCaja m where m.caja.idCaja = :idCaja and m.fecha between :desde and :hasta order by m.fecha desc")
    List<CorteMovimientoDto> detalleCorte(@Param("idCaja") Integer idCaja,
                                           @Param("desde") LocalDateTime desde,
                                           @Param("hasta") LocalDateTime hasta);
}
