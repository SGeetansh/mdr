package com.payu.mdr.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BatchIngestionResponse {
    private int totalReceived;
    private int duplicatesFound;
    private int successfullyPriced;
    private String batchId;
    private String message;
}