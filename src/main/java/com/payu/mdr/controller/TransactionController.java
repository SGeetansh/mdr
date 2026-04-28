package com.payu.mdr.controller;

import com.payu.mdr.dto.BatchIngestionResponse;
import com.payu.mdr.dto.TransactionRequest;
import com.payu.mdr.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/batch")
    public ResponseEntity<BatchIngestionResponse> ingestBatch(
            @RequestBody @Valid List<TransactionRequest> requests
    ) {

        log.info("Received batch of {} transactions", requests.size());
        BatchIngestionResponse response =
                transactionService.processBatch(requests);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}