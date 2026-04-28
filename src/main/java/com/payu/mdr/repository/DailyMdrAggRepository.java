package com.payu.mdr.repository;

import com.payu.mdr.entity.DailyMdrAgg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DailyMdrAggRepository extends JpaRepository<DailyMdrAgg, Long> {

    // Read single row
    Optional<DailyMdrAgg>
    findByTxnDateAndMerchantIdAndPaymentModeAndCardTypeAndCardSchemeAndIbiboCode(
            LocalDate txnDate,
            String merchantId,
            String paymentMode,
            String cardType,
            String cardScheme,
            String ibiboCode
    );

    // Read all rows for accounting job
    List<DailyMdrAgg> findByTxnDate(LocalDate txnDate);

    // Upsert aggregation row
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO daily_mdr_agg (
            txn_date,
            merchant_id,
            payment_mode,
            card_type,
            card_scheme,
            ibibo_code,
            txn_count,
            total_txn_amount,
            total_mdr_amount
        )
        VALUES (
            :txnDate,
            :merchantId,
            :paymentMode,
            :cardType,
            :cardScheme,
            :ibiboCode,
            :txnCount,
            :totalTxnAmount,
            :totalMdrAmount
        )
        ON DUPLICATE KEY UPDATE
            txn_count = VALUES(txn_count),
            total_txn_amount = VALUES(total_txn_amount),
            total_mdr_amount = VALUES(total_mdr_amount),
            updated_at = CURRENT_TIMESTAMP
        """, nativeQuery = true)
    void upsertAggregation(
            @Param("txnDate") LocalDate txnDate,
            @Param("merchantId") String merchantId,
            @Param("paymentMode") String paymentMode,
            @Param("cardType") String cardType,
            @Param("cardScheme") String cardScheme,
            @Param("ibiboCode") String ibiboCode,
            @Param("txnCount") long txnCount,
            @Param("totalTxnAmount") BigDecimal totalTxnAmount,
            @Param("totalMdrAmount") BigDecimal totalMdrAmount
    );
}