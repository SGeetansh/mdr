package com.payu.mdr.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "raw_transactions")
public class RawTransaction {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "txn_id", length = 64)
    private String txnId;

    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "merchant_id", nullable = false, length = 50)
    private String merchantId;

    @Column(name = "txn_date", nullable = false)
    private LocalDateTime txnDate;

    @Column(name = "payment_mode", nullable = false, length = 20)
    private String paymentMode;

    @Column(name = "card_type", length = 20)
    private String cardType;

    @Column(name = "card_scheme", length = 20)
    private String cardScheme;

    @Column(name = "ibibo_code", length = 30)
    private String ibiboCode;

    @Column(name = "txn_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal txnAmount;

    @Column(name = "mdr_amount", precision = 12, scale = 2)
    private BigDecimal mdrAmount;

    @Column(length = 3)
    private String currency = "INR";

    @Column(length = 20)
    private String action = "INIT";

    @Column(name = "txn_status", length = 20)
    private String txnStatus = "INIT";

    @Column(name = "rule_id")
    private Long ruleId;
    
    @Column(name = "batch_id", length = 50)
    private String batchId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "dedup_key_hash", length = 64)
    private String dedupKeyHash;

    @Column(name = "is_duplicate")
    private Boolean isDuplicate = false;

    @Column(name = "trace_key", length = 255)
    private String traceKey;

    @Column(name = "risk_score", precision = 3, scale = 2)
    private BigDecimal riskScore;

    @Column(name = "should_review")
    private Boolean shouldReview = false;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (currency == null) currency = "INR";
        if (isDuplicate == null) isDuplicate = false;
    }
}