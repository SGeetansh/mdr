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
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

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
        
        if (requests.isEmpty()) {
            return BatchIngestionResponse.builder()
                    .totalReceived(0)
                    .duplicatesFound(0)
                    .successfullyPriced(0)
                    .batchId(null)
                    .message("Batch processed successfully")
                    .build();
        }

        log.info("Processing batch of {} transactions", requests.size());

        // ── Idempotency: skip any txnId already in the DB ─────────────────────
        List<String> incomingIds = requests.stream()
                .map(TransactionRequest::getTxnId).toList();
        Set<String> existingIds = new HashSet<>(
                rawTransactionRepository.findAllTxnIdsByTxnIdIn(incomingIds));

        int duplicateCount = 0;
        int pricedCount = 0;
        List<RawTransaction> toSave = new ArrayList<>();

        for (TransactionRequest req : requests) {

            // Skip already-persisted txnIds (idempotent resend)
            if (existingIds.contains(req.getTxnId())) {
                log.info("Idempotent skip: txnId={} already processed", req.getTxnId());
                continue;
            }

            RawTransaction txn = mapToEntity(req);

            // ── Dedup check against already-saved rows in THIS batch ──────────
            // We check toSave list first so same-batch duplicates are caught
            // without needing a DB round-trip for rows not yet committed.
            boolean inBatchDuplicate = toSave.stream()
                    .filter(t -> !t.getIsDuplicate())
                    .anyMatch(t -> t.getDedupKeyHash() != null &&
                            t.getDedupKeyHash().equals(
                                    deduplicationService.computeHash(txn)));

            DeduplicationService.DedupResult dedupResult;
            if (inBatchDuplicate) {
                dedupResult = new DeduplicationService.DedupResult(
                        deduplicationService.computeHash(txn), true);
            } else {
                dedupResult = deduplicationService.check(txn);
            }

            txn.setDedupKeyHash(dedupResult.hash());
            txn.setIsDuplicate(dedupResult.isDuplicate());

            if (dedupResult.isDuplicate()) {
                txn.setMdrAmount(BigDecimal.ZERO);
                duplicateCount++;
            } else {
                MdrRuleEngineService.RuleResult ruleResult = mdrRuleEngineService.applyRule(txn);
                txn.setMdrAmount(ruleResult.mdrAmount());
                txn.setRuleId(ruleResult.ruleId());
                pricedCount++;
            }

            txn.setTraceKey(buildTraceKey(txn));
            toSave.add(txn);
        }

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