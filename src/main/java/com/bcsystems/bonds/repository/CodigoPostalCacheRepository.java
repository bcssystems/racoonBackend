package com.bcsystems.bonds.repository;

import com.bcsystems.bonds.domain.CodigoPostalCache;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CodigoPostalCacheRepository extends JpaRepository<CodigoPostalCache, Integer> {
    Optional<CodigoPostalCache> findByCp(String cp);
}
