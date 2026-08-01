package com.avas.platform.commerce;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="payment_audit_logs",indexes=@Index(name="idx_payment_audit_payment",columnList="paymentId"))
public class PaymentAuditEntity {
    @Id private UUID id; @Column(nullable=false) private UUID paymentId; @Column(nullable=false) private UUID orderId;
    @Column(nullable=false,length=60) private String event; @Column(nullable=false,length=1000) private String detail;
    @Column(nullable=false,updatable=false) private Instant createdAt;
    protected PaymentAuditEntity(){}
    public PaymentAuditEntity(PaymentEntity payment,String event,String detail){this.id=UUID.randomUUID();this.paymentId=payment.getId();this.orderId=payment.getOrderId();this.event=event;this.detail=detail;this.createdAt=Instant.now();}
}
