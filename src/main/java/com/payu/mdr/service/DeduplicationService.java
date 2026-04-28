package com.payu.mdr.service;

import com.payu.mdr.entity.RawTransaction;
import com.payu.mdr.repository.RawTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeduplicationService {

    private final RawTransactionRepository rawTransactionRepository;

    // configurable time window — default 24 hours, override in application.yml
    @Value("${mdr.dedup.window-hours:24}")
    private int dedupWindowHours;

    // ─────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────

    /**
     * Checks if a transaction is a duplicate.
     * Computes the dedup hash, then looks for an existing
     * non-duplicate row with the same hash within the time window.
     *
     * Returns a DedupResult containing:
     *   - the hash (always set — stored on the row regardless)
     *   - isDuplicate flag
     */
    public DedupResult check(RawTransaction txn) {
        String hash = computeHash(txn);
        LocalDateTime windowStart = LocalDateTime.now().minusHours(dedupWindowHours);

        boolean isDuplicate = rawTransactionRepository
                .findFirstByDedupKeyHashAndIsDuplicateFalseAndCreatedAtAfter(hash, windowStart)
                .isPresent();

        if (isDuplicate) {
            log.debug("Duplicate detected: hash={} merchant={} txnId={}",
                    hash, txn.getMerchantId(), txn.getTxnId());
        }

        return new DedupResult(hash, isDuplicate);
    }

    // ─────────────────────────────────────────────
    // HASH COMPUTATION
    // ─────────────────────────────────────────────

    /**
     * Dedup key = merchant_id + DATE(txn_date) + payment_mode + txn_amount
     *
     * We deliberately exclude txn_id and order_id because those are
     * system-generated and differ on every retry even for the same
     * underlying customer payment.
     *
     * txn_amount is included so two different amounts on the same day
     * from the same merchant are NOT considered duplicates.
     */
    public String computeHash(RawTransaction txn) {
        String date = txn.getTxnDate()
                .format(DateTimeFormatter.ISO_LOCAL_DATE); // "2026-04-27"

        String raw = String.join("|",
                nullSafe(txn.getMerchantId()),
                date,
                nullSafe(txn.getPaymentMode()),
                txn.getTxnAmount().toPlainString()
        );

        return sha256(raw);
    }

    // ─────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to exist in all JVMs — this never throws
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    // ─────────────────────────────────────────────
    // RESULT RECORD
    // ─────────────────────────────────────────────

    public record DedupResult(String hash, boolean isDuplicate) {}
}