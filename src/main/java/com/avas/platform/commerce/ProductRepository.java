package com.avas.platform.commerce;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface ProductRepository extends JpaRepository<ProductEntity, Long> {
    List<ProductEntity> findAllByActiveTrueOrderByCategoryAscNameAsc();
    Optional<ProductEntity> findByCodeAndActiveTrue(String code);
    boolean existsByCode(String code);
}
