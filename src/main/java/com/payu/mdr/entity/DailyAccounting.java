package com.payu.mdr.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "daily_accounting")
public class DailyAccounting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false, length = 50)
    private String merchantId;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(name = "txn_count")
    private Long txnCount = 0L;

    @Column(name = "gross_txn_amount", precision = 18, scale = 2)
    private BigDecimal grossTxnAmount = BigDecimal.ZERO;

    @Column(name = "total_mdr_amount", precision = 18, scale = 2)
    private BigDecimal totalMdrAmount = BigDecimal.ZERO;

    @Column(name = "net_settlement_amount", precision = 18, scale = 2)
    private BigDecimal netSettlementAmount = BigDecimal.ZERO;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}