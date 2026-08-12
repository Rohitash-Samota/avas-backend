package com.avas.platform.commerce;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {
    Optional<PaymentEntity> findByPublicId(UUID publicId);
    Optional<PaymentEntity> findByOrderId(UUID orderId);
    Optional<PaymentEntity> findByGatewayOrderId(String gatewayOrderId);
}
