package com.avas.platform.commerce;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="wallets",indexes=@Index(name="idx_wallet_user",columnList="userId",unique=true))
public class WalletEntity {
    @Id private UUID id; @Column(nullable=false,unique=true) private UUID userId; @Column(nullable=false) private long balance;
    @Column(nullable=false,length=3) private String currency; @Column(nullable=false,length=20) private String status;
    @Version private long entityVersion; @Column(nullable=false) private Instant updatedAt;
    protected WalletEntity(){}
    public WalletEntity(UUID userId){this.id=UUID.randomUUID();this.userId=userId;this.balance=0;this.currency="INR";this.status="ACTIVE";this.updatedAt=Instant.now();}
    public void credit(long amount){balance=Math.addExact(balance,amount);updatedAt=Instant.now();}
    public void debit(long amount){if(amount>balance)throw new IllegalArgumentException("Insufficient wallet balance");balance-=amount;updatedAt=Instant.now();}
    public UUID getId(){return id;} public UUID getUserId(){return userId;} public long getBalance(){return balance;} public String getCurrency(){return currency;} public String getStatus(){return status;} public Instant getUpdatedAt(){return updatedAt;}
}
