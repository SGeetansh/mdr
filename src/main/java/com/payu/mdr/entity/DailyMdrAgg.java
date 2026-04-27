package com.payu.mdr.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "daily_mdr_agg")
public class DailyMdrAgg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(name = "merchant_id", nullable = false, length = 50)
    private String merchantId;

    @Column(name = "payment_mode", length = 20)
    private String paymentMode;

    @Column(name = "card_type", length = 20)
    private String cardType;

    @Column(name = "card_scheme", length = 20)
    private String cardScheme;

    @Column(name = "ibibo_code", length = 30)
    private String ibiboCode;

    @Column(name = "txn_count")
    private Long txnCount = 0L;

    @Column(name = "total_txn_amount", precision = 18, scale = 2)
    private BigDecimal totalTxnAmount = BigDecimal.ZERO;

    @Column(name = "total_mdr_amount", precision = 18, scale = 2)
    private BigDecimal totalMdrAmount = BigDecimal.ZERO;

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