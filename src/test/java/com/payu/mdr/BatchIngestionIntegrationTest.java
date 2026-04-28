package com.payu.mdr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payu.mdr.dto.TransactionRequest;
import com.payu.mdr.entity.RawTransaction;
import com.payu.mdr.repository.RawTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BatchIngestionIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired RawTransactionRepository rawRepo;

    // Fixed timestamp so all txns in a test share the same date —
    // required for dedup hash to match (hash includes DATE(txn_date))
    private static final LocalDateTime TXN_TIME =
            LocalDateTime.of(2026, 4, 28, 10, 0, 0);

    @BeforeEach
    void clearDb() {
        rawRepo.deleteAll();
    }



    // ── Test 1: dedup and MDR are applied correctly in a single batch ─────

    @Test
    void batchIngestion_dedupAndPricing_worksCorrectly() throws Exception {

        // TXN-001 and TXN-003 have identical dedup keys:
        //   merchant_id=22137, date=2026-04-28, payment_mode=CC, amount=1000
        // TXN-003 should be flagged as duplicate.
        List<TransactionRequest> batch = List.of(
                makeTxnRequest("TXN-001", "ORD-001", "22137",
                        "CC", "CREDIT", "VISA", new BigDecimal("1000.00")),
                makeTxnRequest("TXN-002", "ORD-002", "22137",
                        "UPI", null, null, new BigDecimal("500.00")),
                makeTxnRequest("TXN-003", "ORD-003", "22137",
                        "CC", "CREDIT", "VISA", new BigDecimal("1000.00"))  // duplicate of TXN-001
        );

        mockMvc.perform(post("/api/v1/transactions/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batch)))
                .andExpect(status().isCreated())                      // 201
                .andExpect(jsonPath("$.totalReceived").value(3))
                .andExpect(jsonPath("$.duplicatesFound").value(1))
                .andExpect(jsonPath("$.successfullyPriced").value(2))
                .andExpect(jsonPath("$.batchId").value("BATCH-TEST-001"));

        // ── Verify DB state ───────────────────────────────────────────────

        // TXN-003 must be flagged duplicate
        RawTransaction dup = findByTxnId("TXN-003");
        assertThat(dup.getIsDuplicate()).isTrue();
        assertThat(dup.getMdrAmount()).isEqualByComparingTo("0.00"); // not priced

        // TXN-001: merchant 22137 + CC + CREDIT + VISA → seed rule gives 1.50%
        // 1.50% of 1000 = 15.00
        RawTransaction ccTxn = findByTxnId("TXN-001");
        assertThat(ccTxn.getIsDuplicate()).isFalse();
        assertThat(ccTxn.getMdrAmount()).isEqualByComparingTo("15.00");
        assertThat(ccTxn.getRuleId()).isNotNull();
        assertThat(ccTxn.getTraceKey()).contains("22137");

        // TXN-002: UPI → seed rule gives 0.00%
        // 0.00% of 500 = 0.00
        RawTransaction upiTxn = findByTxnId("TXN-002");
        assertThat(upiTxn.getIsDuplicate()).isFalse();
        assertThat(upiTxn.getMdrAmount()).isEqualByComparingTo("0.00");
    }

    // ── Test 2: re-sending the same batch must not double-count ──────────

    @Test
    void resendingSameBatch_isIdempotent_noDoubleCount() throws Exception {
        List<TransactionRequest> batch = List.of(
                makeTxnRequest("TXN-IDEM-001", "ORD-IDEM-001", "22137",
                        "CC", "CREDIT", "VISA", new BigDecimal("1000.00"))
        );

        String payload = objectMapper.writeValueAsString(batch);

        // Send the same batch twice
        mockMvc.perform(post("/api/v1/transactions/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/transactions/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated());

        // Exactly one row in DB — no double insert
        long count = rawRepo.findAll().stream()
                .filter(t -> t.getTxnId().equals("TXN-IDEM-001"))
                .count();
        assertThat(count).isEqualTo(1);
    }

    // ── Test 3: empty batch returns a clean response ──────────────────────

    @Test
    void emptyBatch_returnsZeroCounts() throws Exception {
        mockMvc.perform(post("/api/v1/transactions/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalReceived").value(0))
                .andExpect(jsonPath("$.duplicatesFound").value(0))
                .andExpect(jsonPath("$.successfullyPriced").value(0));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private TransactionRequest makeTxnRequest(
            String txnId, String orderId, String merchantId,
            String paymentMode, String cardType, String cardScheme,
            BigDecimal amount) {

        TransactionRequest req = new TransactionRequest();
        req.setTxnId(txnId);
        req.setOrderId(orderId);
        req.setMerchantId(merchantId);
        req.setTxnDate(TXN_TIME);
        req.setPaymentMode(paymentMode);
        req.setCardType(cardType);
        req.setCardScheme(cardScheme);
        req.setTxnAmount(amount);
        req.setCurrency("INR");
        req.setAction("INIT");
        req.setTxnStatus("INIT");
        req.setBatchId("BATCH-TEST-001");
        return req;
    }

    private RawTransaction findByTxnId(String txnId) {
        return rawRepo.findAll().stream()
                .filter(t -> t.getTxnId().equals(txnId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Transaction not found: " + txnId));
    }
}