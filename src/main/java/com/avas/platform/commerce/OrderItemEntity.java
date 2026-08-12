package com.avas.platform.commerce;

import com.avas.platform.common.persistence.AbstractLongIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "commerce_order_items")
public class OrderItemEntity extends AbstractLongIdEntity {
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(nullable = false, length = 80)
    private String productCode;

    @Column(nullable = false, length = 160)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private long unitPrice;

    @Column(nullable = false)
    private long lineTotal;

    protected OrderItemEntity() {
    }

    public OrderItemEntity(OrderEntity order, String productCode, String productName, int quantity, long unitPrice) {
        this.publicId = UUID.randomUUID();
        this.order = order;
        this.productCode = productCode;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.lineTotal = Math.multiplyExact(unitPrice, quantity);
    }

    public String getProductCode() { return productCode; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public long getUnitPrice() { return unitPrice; }
    public long getLineTotal() { return lineTotal; }
}
