package com.avas.platform.commerce;

import com.avas.platform.common.persistence.AbstractLongIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_refunds", indexes = @Index(name = "idx_refund_payment", columnList = "paymentId"))
public class RefundEntity extends AbstractLongIdEntity {
    public enum Status { REQUESTED, SUCCEEDED, FAILED }

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(nullable = false)
    private UUID paymentId;

    @Column(nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private UUID requestedBy;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(length = 120)
    private String gatewayRefundId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected RefundEntity() {
    }

    public RefundEntity(PaymentEntity payment, UUID requestedBy, long amount, String reason) {
        this.publicId = UUID.randomUUID();
        this.paymentId = payment.getId();
        this.orderId = payment.getOrderId();
        this.requestedBy = requestedBy;
        this.amount = amount;
        this.reason = reason;
        this.status = Status.REQUESTED;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void succeeded(String gatewayRefundId) {
        this.status = Status.SUCCEEDED;
        this.gatewayRefundId = gatewayRefundId;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return publicId; }
    public long getAmount() { return amount; }
    public String getReason() { return reason; }
    public Status getStatus() { return status; }
    public String getGatewayRefundId() { return gatewayRefundId; }
}
