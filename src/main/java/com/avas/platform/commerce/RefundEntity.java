package com.avas.platform.commerce;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="payment_refunds",indexes=@Index(name="idx_refund_payment",columnList="paymentId"))
public class RefundEntity {
    public enum Status { REQUESTED, SUCCEEDED, FAILED }
    @Id private UUID id; @Column(nullable=false) private UUID paymentId; @Column(nullable=false) private UUID orderId;
    @Column(nullable=false) private UUID requestedBy; @Column(nullable=false) private long amount;
    @Column(nullable=false,length=500) private String reason; @Column(length=120) private String gatewayRefundId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(nullable=false,updatable=false) private Instant createdAt; @Column(nullable=false) private Instant updatedAt;
    protected RefundEntity(){}
    public RefundEntity(PaymentEntity payment,UUID requestedBy,long amount,String reason){this.id=UUID.randomUUID();this.paymentId=payment.getId();this.orderId=payment.getOrderId();this.requestedBy=requestedBy;this.amount=amount;this.reason=reason;this.status=Status.REQUESTED;this.createdAt=Instant.now();this.updatedAt=createdAt;}
    public void succeeded(String gatewayRefundId){this.status=Status.SUCCEEDED;this.gatewayRefundId=gatewayRefundId;this.updatedAt=Instant.now();}
    public UUID getId(){return id;} public long getAmount(){return amount;} public String getReason(){return reason;} public Status getStatus(){return status;} public String getGatewayRefundId(){return gatewayRefundId;}
}
