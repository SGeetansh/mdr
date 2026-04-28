package com.payu.mdr.controller;

import com.payu.mdr.dto.BatchIngestionResponse;
import com.payu.mdr.dto.TransactionRequest;
import com.payu.mdr.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/batch")
    public ResponseEntity<BatchIngestionResponse> ingestBatch(
            @RequestBody List<TransactionRequest> requests
    ) {
        BatchIngestionResponse response =
                transactionService.processBatch(requests);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}