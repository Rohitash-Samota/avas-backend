package com.avas.platform.commerce;

import com.avas.platform.common.persistence.AbstractLongIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_audit_logs", indexes = @Index(name = "idx_payment_audit_payment", columnList = "paymentId"))
public class PaymentAuditEntity extends AbstractLongIdEntity {
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(nullable = false)
    private UUID paymentId;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false, length = 60)
    private String event;

    @Column(nullable = false, length = 1000)
    private String detail;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected PaymentAuditEntity() {
    }

    public PaymentAuditEntity(PaymentEntity payment, String event, String detail) {
        this.publicId = UUID.randomUUID();
        this.paymentId = payment.getId();
        this.orderId = payment.getOrderId();
        this.event = event;
        this.detail = detail;
        this.createdAt = Instant.now();
    }
}
