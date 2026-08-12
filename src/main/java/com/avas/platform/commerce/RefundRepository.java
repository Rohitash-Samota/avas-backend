package com.avas.platform.commerce;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface RefundRepository extends JpaRepository<RefundEntity, Long> {
    List<RefundEntity> findAllByOrderId(UUID orderId);
}
