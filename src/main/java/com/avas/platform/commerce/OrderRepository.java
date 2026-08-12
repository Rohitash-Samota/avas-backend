package com.avas.platform.commerce;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface OrderRepository extends JpaRepository<OrderEntity, Long> {
    Optional<OrderEntity> findByPublicId(UUID publicId);
    List<OrderEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
