package com.avas.platform.commerce;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "catalog_products", indexes = @Index(name = "idx_product_active", columnList = "active"))
public class ProductEntity {
    @Id private UUID id;
    @Column(nullable = false, unique = true, length = 80) private String code;
    @Column(nullable = false, length = 160) private String name;
    @Column(nullable = false, length = 1000) private String description;
    @Column(nullable = false, length = 80) private String category;
    @Column(nullable = false) private long unitPrice;
    @Column(nullable = false, length = 8) private String icon;
    @Column(nullable = false) private boolean active;
    @Column(nullable = false) private Instant updatedAt;

    protected ProductEntity() {}
    public ProductEntity(String code, String name, String description, String category, long unitPrice, String icon) {
        this.id = UUID.randomUUID(); this.code = code; this.name = name; this.description = description;
        this.category = category; this.unitPrice = unitPrice; this.icon = icon; this.active = true; this.updatedAt = Instant.now();
    }
    public UUID getId(){return id;} public String getCode(){return code;} public String getName(){return name;}
    public String getDescription(){return description;} public String getCategory(){return category;}
    public long getUnitPrice(){return unitPrice;} public String getIcon(){return icon;} public boolean isActive(){return active;}
}
