package com.payu.mdr.repository;

import com.payu.mdr.entity.HourlyMdrAgg;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface HourlyMdrAggRepository extends JpaRepository<HourlyMdrAgg, Long> {

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO hourly_mdr_agg (
            window_start,
            window_end,
            txn_date,
            txn_hour,
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
            :windowStart,
            :windowEnd,
            :txnDate,
            :txnHour,
            :merchantId,
            COALESCE(:paymentMode, ''),
            COALESCE(:cardType, ''),
            COALESCE(:cardScheme, ''),
            COALESCE(:ibiboCode, ''),
            :txnCount,
            :totalTxnAmount,
            :totalMdrAmount
        )
        ON DUPLICATE KEY UPDATE
            window_end = VALUES(window_end),
            txn_count = VALUES(txn_count),
            total_txn_amount = VALUES(total_txn_amount),
            total_mdr_amount = VALUES(total_mdr_amount),
            updated_at = CURRENT_TIMESTAMP
        """, nativeQuery = true)
    void upsertHourlyAggregation(
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd,
            @Param("txnDate") LocalDate txnDate,
            @Param("txnHour") int txnHour,
            @Param("merchantId") String merchantId,
            @Param("paymentMode") String paymentMode,
            @Param("cardType") String cardType,
            @Param("cardScheme") String cardScheme,
            @Param("ibiboCode") String ibiboCode,
            @Param("txnCount") long txnCount,
            @Param("totalTxnAmount") BigDecimal totalTxnAmount,
            @Param("totalMdrAmount") BigDecimal totalMdrAmount
    );

    @Query(value = """
        SELECT
            txn_date,
            merchant_id,
            payment_mode,
            card_type,
            card_scheme,
            ibibo_code,
            SUM(txn_count) AS txn_count,
            SUM(total_txn_amount) AS total_txn_amount,
            SUM(total_mdr_amount) AS total_mdr_amount
        FROM hourly_mdr_agg
        WHERE txn_date = :txnDate
        GROUP BY
            txn_date,
            merchant_id,
            payment_mode,
            card_type,
            card_scheme,
            ibibo_code
        """, nativeQuery = true)
    List<Object[]> aggregateForDate(@Param("txnDate") LocalDate txnDate);
}
