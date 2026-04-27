package com.payu.mdr.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "mdr_pricing_rules")
public class MdrPricingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rule_id")
    private Long ruleId;

    @Column(name = "merchant_id", length = 50)
    private String merchantId;         // NULL = default rule

    @Column(name = "payment_mode", length = 20)
    private String paymentMode;

    @Column(name = "card_type", length = 20)
    private String cardType;

    @Column(name = "card_scheme", length = 20)
    private String cardScheme;

    @Column(name = "ibibo_code", length = 30)
    private String ibiboCode;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "mdr_rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal mdrRatePercent;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "created_by", length = 50)
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}