package com.bcsystems.bonds.service.impl;

import com.bcsystems.bonds.domain.Caja;
import com.bcsystems.bonds.domain.Sucursal;
import com.bcsystems.bonds.domain.en.CajaEstado;
import com.bcsystems.bonds.domain.en.TipoCaja;
import com.bcsystems.bonds.repository.CajaRepository;
import com.bcsystems.bonds.repository.SucursalRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatosInicialesService {

    private final SucursalRepository sucursalRepository;
    private final CajaRepository cajaRepository;
    private final JdbcTemplate jdbcTemplate;

    public DatosInicialesService(SucursalRepository sucursalRepository,
                                 CajaRepository cajaRepository,
                                 JdbcTemplate jdbcTemplate) {
        this.sucursalRepository = sucursalRepository;
        this.cajaRepository = cajaRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    @Transactional
    public void inicializar() {
        // Convert persisted values before Hibernate materializes TipoCaja.
        jdbcTemplate.execute("ALTER TABLE caja MODIFY tipo ENUM('NORMAL','CHICA','PRINCIPAL','SECUNDARIA') NOT NULL");
        jdbcTemplate.update("UPDATE caja SET tipo = 'PRINCIPAL' WHERE tipo = 'NORMAL'");
        jdbcTemplate.update("UPDATE caja SET tipo = 'SECUNDARIA' WHERE tipo = 'CHICA'");
        jdbcTemplate.execute("ALTER TABLE caja MODIFY tipo ENUM('PRINCIPAL','SECUNDARIA') NOT NULL");
        Sucursal sucursal = sucursalRepository.findByActivaTrueOrderByNombreAsc().stream()
                .findFirst()
                .orElse(null);
        if (sucursal == null && sucursalRepository.count() == 0) {
            sucursal = sucursalRepository.save(Sucursal.builder()
                    .nombre("Sucursal Principal")
                    .direccion("")
                    .telefono("")
                    .activa(true)
                    .build());
        }
        if (sucursal == null) {
            return;
        }

        boolean existePrincipal = cajaRepository.findBySucursalIdSucursalAndActivaTrue(sucursal.getIdSucursal()).stream()
                .anyMatch(c -> c.getTipo() == TipoCaja.PRINCIPAL);
        if (!existePrincipal) {
            cajaRepository.save(Caja.builder()
                    .nombre("Caja Principal")
                    .tipo(TipoCaja.PRINCIPAL)
                    .sucursal(sucursal)
                    .estado(CajaEstado.CERRADA)
                    .saldoActual(0.0)
                    .activa(true)
                    .build());
        }

    }
}
