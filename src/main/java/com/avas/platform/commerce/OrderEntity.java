package com.avas.platform.commerce;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "commerce_orders", indexes = {
        @Index(name="idx_orders_user", columnList="userId"), @Index(name="idx_orders_tenant", columnList="tenantId"),
        @Index(name="idx_orders_project", columnList="projectId")})
public class OrderEntity {
    public enum Status { PENDING_PAYMENT, PAID, CANCELLED, REFUNDED }
    public enum Type { REGULAR, TOPUP }
    @Id private UUID id;
    @Column(nullable=false) private UUID userId;
    @Column(nullable=false,length=60) private String tenantId;
    @Column(length=80) private String projectId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private Status status;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Type type;
    @Column(nullable=false,length=3) private String currency;
    @Column(nullable=false) private long total;
    @Column(nullable=false,length=160) private String buyerName;
    @Column(length=190) private String buyerEmail;
    @Column(length=30) private String buyerPhone;
    @OneToMany(mappedBy="order",cascade=CascadeType.ALL,orphanRemoval=true,fetch=FetchType.EAGER)
    private List<OrderItemEntity> items=new ArrayList<>();
    @Column(nullable=false,updatable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    protected OrderEntity(){}
    public OrderEntity(UUID userId,String tenantId,String projectId,Type type,long total,String buyerName,String buyerEmail,String buyerPhone){
        this.id=UUID.randomUUID();this.userId=userId;this.tenantId=tenantId;this.projectId=projectId;this.type=type;
        this.status=Status.PENDING_PAYMENT;this.currency="INR";this.total=total;this.buyerName=buyerName;this.buyerEmail=buyerEmail;
        this.buyerPhone=buyerPhone;this.createdAt=Instant.now();this.updatedAt=createdAt;
    }
    public void addItem(OrderItemEntity item){items.add(item);}
    public void paid(){status=Status.PAID;updatedAt=Instant.now();}
    public void refunded(){status=Status.REFUNDED;updatedAt=Instant.now();}
    public UUID getId(){return id;} public UUID getUserId(){return userId;} public String getTenantId(){return tenantId;}
    public String getProjectId(){return projectId;} public Status getStatus(){return status;} public Type getType(){return type;}
    public String getCurrency(){return currency;} public long getTotal(){return total;} public String getBuyerName(){return buyerName;}
    public String getBuyerEmail(){return buyerEmail;} public String getBuyerPhone(){return buyerPhone;}
    public List<OrderItemEntity> getItems(){return List.copyOf(items);} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;}
}
