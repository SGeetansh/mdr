package com.payu.mdr;

import com.payu.mdr.entity.RawTransaction;
import com.payu.mdr.repository.RawTransactionRepository;
import com.payu.mdr.service.DeduplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeduplicationServiceTest {

    @Mock
    private RawTransactionRepository rawTransactionRepository;

    @InjectMocks
    private DeduplicationService deduplicationService;

    @BeforeEach
    void setUp() {
        // inject the @Value field since we're not loading Spring context
        ReflectionTestUtils.setField(deduplicationService, "dedupWindowHours", 24);
    }

    // ── Test 1: unique transaction — no duplicate in DB ───────────────────

    @Test
    void whenNoExistingHash_thenNotDuplicate() {
        when(rawTransactionRepository
                .findFirstByDedupKeyHashAndIsDuplicateFalseAndCreatedAtAfter(any(), any()))
                .thenReturn(Optional.empty());

        RawTransaction txn = makeTxn("M001", "CC", "1000.00", LocalDateTime.now());
        DeduplicationService.DedupResult result = deduplicationService.check(txn);

        assertThat(result.isDuplicate()).isFalse();
        assertThat(result.hash()).isNotBlank();
    }

    // ── Test 2: same transaction within window = duplicate ────────────────

    @Test
    void whenSameHashExistsWithinWindow_thenIsDuplicate() {
        RawTransaction existing = makeTxn("M001", "CC", "1000.00", LocalDateTime.now());

        when(rawTransactionRepository
                .findFirstByDedupKeyHashAndIsDuplicateFalseAndCreatedAtAfter(any(), any()))
                .thenReturn(Optional.of(existing));

        RawTransaction incoming = makeTxn("M001", "CC", "1000.00", LocalDateTime.now());
        DeduplicationService.DedupResult result = deduplicationService.check(incoming);

        assertThat(result.isDuplicate()).isTrue();
    }

    // ── Test 3: same transaction OUTSIDE window = not a duplicate ─────────

    @Test
    void whenSameHashExistsOutsideWindow_thenNotDuplicate() {
        // DB returns empty because the query filters by created_at > windowStart
        // A row older than 24h would not be returned by the query
        when(rawTransactionRepository
                .findFirstByDedupKeyHashAndIsDuplicateFalseAndCreatedAtAfter(any(), any()))
                .thenReturn(Optional.empty());

        RawTransaction txn = makeTxn("M001", "CC", "1000.00",
                LocalDateTime.now().minusHours(25));
        DeduplicationService.DedupResult result = deduplicationService.check(txn);

        assertThat(result.isDuplicate()).isFalse();
    }

    // ── Test 4: different amount = different hash = not duplicate ─────────

    @Test
    void whenAmountDiffers_thenHashDiffers() {
        RawTransaction txn1 = makeTxn("M001", "CC", "1000.00", LocalDateTime.now());
        RawTransaction txn2 = makeTxn("M001", "CC", "1001.00", LocalDateTime.now());

        String hash1 = deduplicationService.computeHash(txn1);
        String hash2 = deduplicationService.computeHash(txn2);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    // ── Test 5: different merchant = different hash = not duplicate ───────

    @Test
    void whenMerchantDiffers_thenHashDiffers() {
        RawTransaction txn1 = makeTxn("M001", "CC", "1000.00", LocalDateTime.now());
        RawTransaction txn2 = makeTxn("M002", "CC", "1000.00", LocalDateTime.now());

        String hash1 = deduplicationService.computeHash(txn1);
        String hash2 = deduplicationService.computeHash(txn2);

        assertThat(hash1).isNotEqualTo(hash2);
    }

    // ── Test 6: same inputs always produce same hash (deterministic) ──────

    @Test
    void hashIsDeterministic() {
        RawTransaction txn = makeTxn("M001", "CC", "1000.00", LocalDateTime.now());

        String hash1 = deduplicationService.computeHash(txn);
        String hash2 = deduplicationService.computeHash(txn);

        assertThat(hash1).isEqualTo(hash2);
    }

    // ── Test 7: hash is always 64 chars (SHA-256 = 32 bytes = 64 hex) ─────

    @Test
    void hashIsAlways64Characters() {
        RawTransaction txn = makeTxn("M001", "UPI", "500.00", LocalDateTime.now());
        String hash = deduplicationService.computeHash(txn);
        assertThat(hash).hasSize(64);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private RawTransaction makeTxn(String merchantId, String paymentMode,
                                    String amount, LocalDateTime txnDate) {
        RawTransaction txn = new RawTransaction();
        txn.setMerchantId(merchantId);
        txn.setPaymentMode(paymentMode);
        txn.setTxnAmount(new BigDecimal(amount));
        txn.setTxnDate(txnDate);
        return txn;
    }
}