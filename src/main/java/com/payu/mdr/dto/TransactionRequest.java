package com.payu.mdr.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class TransactionRequest {

    @NotBlank(message = "txnId is required")
    private String txnId;

    @NotBlank(message = "orderId is required")
    private String orderId;

    @NotBlank(message = "merchantId is required")
    private String merchantId;

    @NotNull(message = "txnDate is required")
    private LocalDateTime txnDate;

    @NotBlank(message = "paymentMode is required")
    private String paymentMode;

    private String cardType;
    private String cardScheme;
    private String ibiboCode;

    @NotNull(message = "txnAmount is required")
    @Positive(message = "txnAmount must be positive")
    private BigDecimal txnAmount;

    private String currency = "INR";
    private String action = "INIT";
    private String txnStatus = "INIT";

    @NotBlank(message = "batchId is required")
    private String batchId;
}