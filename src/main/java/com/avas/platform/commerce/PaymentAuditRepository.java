package com.avas.platform.commerce;

import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentAuditRepository extends JpaRepository<PaymentAuditEntity, Long> {
}
