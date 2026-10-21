package com.bcsystems.bonds.repository;

import com.bcsystems.bonds.domain.Persona;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface PersonaRepository extends JpaRepository<Persona, Integer> {
    Optional<Persona> findByUsuarioIgnoreCase(String usuario);
    Optional<Persona> findByUsuario(String usuario);
    boolean existsByUsuarioIgnoreCase(String usuario);
    boolean existsByUsuarioIgnoreCaseAndIdPersonaNot(String usuario, Integer idPersona);
    Page<Persona> findByActiva(Boolean activa, Pageable pageable);
    List<Persona> findByActivaTrueOrderByNombreAsc();
    List<Persona> findByRolNombreIgnoreCaseAndActivaTrue(String nombreRol);
    @Query("select p from Persona p where p.rol is null")
    List<Persona> findPendientesMigracionRol();
}
