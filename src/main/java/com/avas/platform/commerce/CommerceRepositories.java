package com.avas.platform.commerce;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ProductRepository extends JpaRepository<ProductEntity,UUID>{List<ProductEntity> findAllByActiveTrueOrderByCategoryAscNameAsc();Optional<ProductEntity> findByCodeAndActiveTrue(String code);boolean existsByCode(String code);}
interface OrderRepository extends JpaRepository<OrderEntity,UUID>{List<OrderEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);}
interface PaymentRepository extends JpaRepository<PaymentEntity,UUID>{Optional<PaymentEntity> findByOrderId(UUID orderId);Optional<PaymentEntity> findByGatewayOrderId(String gatewayOrderId);}
interface RefundRepository extends JpaRepository<RefundEntity,UUID>{List<RefundEntity> findAllByOrderId(UUID orderId);}
interface WalletRepository extends JpaRepository<WalletEntity,UUID>{Optional<WalletEntity> findByUserId(UUID userId);}
interface WalletTransactionRepository extends JpaRepository<WalletTransactionEntity,UUID>{List<WalletTransactionEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);}
interface PaymentAuditRepository extends JpaRepository<PaymentAuditEntity,UUID>{}
