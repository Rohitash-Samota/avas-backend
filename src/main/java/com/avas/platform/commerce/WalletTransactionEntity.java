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
@Table(name = "wallet_transactions", indexes = @Index(name = "idx_wallet_tx_user", columnList = "userId"))
public class WalletTransactionEntity extends AbstractLongIdEntity {
    public enum Type { CREDIT, DEBIT, REFUND_REVERSAL }

    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;

    @Column(nullable = false)
    private UUID walletId;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Type type;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long balanceAfter;

    @Column(nullable = false, length = 80)
    private String referenceType;

    @Column(nullable = false, length = 120)
    private String referenceId;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected WalletTransactionEntity() {
    }

    public WalletTransactionEntity(WalletEntity wallet, Type type, long amount, String referenceType,
            String referenceId, String description) {
        this.publicId = UUID.randomUUID();
        this.walletId = wallet.getId();
        this.userId = wallet.getUserId();
        this.type = type;
        this.amount = amount;
        this.balanceAfter = wallet.getBalance();
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.description = description;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return publicId; }
    public Type getType() { return type; }
    public long getAmount() { return amount; }
    public long getBalanceAfter() { return balanceAfter; }
    public String getReferenceType() { return referenceType; }
    public String getReferenceId() { return referenceId; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
