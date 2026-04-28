package com.payu.mdr.service;

import com.payu.mdr.dto.BatchIngestionResponse;
import com.payu.mdr.dto.TransactionRequest;
import com.payu.mdr.entity.RawTransaction;
import com.payu.mdr.repository.RawTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final RawTransactionRepository rawTransactionRepository;
    private final DeduplicationService deduplicationService;
    private final MdrRuleEngineService mdrRuleEngineService;

    // ─────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────

    @Transactional
    public BatchIngestionResponse processBatch(List<TransactionRequest> requests) {
        log.info("Processing batch of {} transactions", requests.size());

        int duplicateCount = 0;
        int pricedCount = 0;
        List<RawTransaction> toSave = new ArrayList<>();

        for (TransactionRequest req : requests) {

            // Step 1 — map request to entity
            RawTransaction txn = mapToEntity(req);

            // Step 2 — dedup check
            DeduplicationService.DedupResult dedupResult = deduplicationService.check(txn);
            txn.setDedupKeyHash(dedupResult.hash());
            txn.setIsDuplicate(dedupResult.isDuplicate());

            if (dedupResult.isDuplicate()) {
                duplicateCount++;
                log.debug("Duplicate txn skipped for pricing: txnId={}", txn.getTxnId());
            } else {
                // Step 3 — apply MDR rule (only for non-duplicates)
                MdrRuleEngineService.RuleResult ruleResult = mdrRuleEngineService.applyRule(txn);
                txn.setMdrAmount(ruleResult.mdrAmount());
                txn.setRuleId(ruleResult.ruleId());
                pricedCount++;
            }

            // Step 4 — set trace key (always, even for duplicates)
            txn.setTraceKey(buildTraceKey(txn));

            toSave.add(txn);
        }

        // Step 5 — bulk save everything in one DB round trip
        rawTransactionRepository.saveAll(toSave);

        log.info("Batch complete: total={} duplicates={} priced={}",
                requests.size(), duplicateCount, pricedCount);

        return BatchIngestionResponse.builder()
                .totalReceived(requests.size())
                .duplicatesFound(duplicateCount)
                .successfullyPriced(pricedCount)
                .batchId(requests.get(0).getBatchId())
                .message("Batch processed successfully")
                .build();
    }

    // ─────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────

    private RawTransaction mapToEntity(TransactionRequest req) {
        RawTransaction txn = new RawTransaction();

        // generate a unique row ID
        txn.setId(UUID.randomUUID().toString());

        txn.setTxnId(req.getTxnId());
        txn.setOrderId(req.getOrderId());
        txn.setMerchantId(req.getMerchantId());
        txn.setTxnDate(req.getTxnDate());
        txn.setPaymentMode(req.getPaymentMode());
        txn.setCardType(req.getCardType());
        txn.setCardScheme(req.getCardScheme());
        txn.setIbiboCode(req.getIbiboCode());
        txn.setTxnAmount(req.getTxnAmount());
        txn.setCurrency(req.getCurrency());
        txn.setAction(req.getAction());
        txn.setTxnStatus(req.getTxnStatus());
        txn.setBatchId(req.getBatchId());

        return txn;
    }

    /**
     * Trace key format: merchantId:DATE:hour:txnId
     *
     * This lets you follow any single transaction from raw_transactions
     * → daily_mdr_agg → daily_accounting just by parsing the trace key.
     *
     * Example: "22137:2026-04-27:14:TXN-001"
     *   - merchantId = 22137
     *   - date       = 2026-04-27  → matches daily_mdr_agg.txn_date
     *   - hour       = 14          → matches the hourly aggregation window
     *   - txnId      = TXN-001     → links back to this exact raw row
     */
    private String buildTraceKey(RawTransaction txn) {
        String date = txn.getTxnDate()
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
        String hour = String.valueOf(txn.getTxnDate().getHour());
        return String.join(":", txn.getMerchantId(), date, hour, txn.getTxnId());
    }
}