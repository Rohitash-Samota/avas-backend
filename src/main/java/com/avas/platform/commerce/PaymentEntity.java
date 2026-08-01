package com.avas.platform.commerce;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="payments",indexes={@Index(name="idx_payments_order",columnList="orderId",unique=true),@Index(name="idx_payments_user",columnList="userId")})
public class PaymentEntity {
    public enum Status { CREATED, PAID, FAILED, REFUNDED }
    @Id private UUID id;
    @Column(nullable=false,unique=true) private UUID orderId;
    @Column(nullable=false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Status status;
    @Column(nullable=false,length=30) private String provider;
    @Column(nullable=false,length=10) private String mode;
    @Column(nullable=false,unique=true,length=120) private String gatewayOrderId;
    @Column(unique=true,length=120) private String gatewayPaymentId;
    @Column(nullable=false,length=120) private String publicKey;
    @Column(nullable=false) private long amount;
    @Column(nullable=false,length=3) private String currency;
    @Column(nullable=false) private boolean checkoutReady;
    @Column(nullable=false,updatable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    protected PaymentEntity(){}
    public PaymentEntity(OrderEntity order,String mode,String gatewayOrderId,String publicKey,boolean checkoutReady){
        this.id=UUID.randomUUID();this.orderId=order.getId();this.userId=order.getUserId();this.status=Status.CREATED;
        this.provider="RAZORPAY";this.mode=mode;this.gatewayOrderId=gatewayOrderId;this.publicKey=publicKey;
        this.amount=order.getTotal();this.currency=order.getCurrency();this.checkoutReady=checkoutReady;
        this.createdAt=Instant.now();this.updatedAt=createdAt;
    }
    public void paid(String gatewayPaymentId){this.status=Status.PAID;this.gatewayPaymentId=gatewayPaymentId;this.updatedAt=Instant.now();}
    public void refunded(){this.status=Status.REFUNDED;this.updatedAt=Instant.now();}
    public UUID getId(){return id;} public UUID getOrderId(){return orderId;} public UUID getUserId(){return userId;}
    public Status getStatus(){return status;} public String getProvider(){return provider;} public String getMode(){return mode;}
    public String getGatewayOrderId(){return gatewayOrderId;} public String getGatewayPaymentId(){return gatewayPaymentId;}
    public String getPublicKey(){return publicKey;} public long getAmount(){return amount;} public String getCurrency(){return currency;}
    public boolean isCheckoutReady(){return checkoutReady;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
