package com.payu.mdr.repository;

import com.payu.mdr.entity.RawTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RawTransactionRepository extends JpaRepository<RawTransaction, String> {

    // ── Used by DeduplicationService ──────────────────────────────────────────
    Optional<RawTransaction> findFirstByDedupKeyHashAndIsDuplicateFalseAndCreatedAtAfter(
            String dedupKeyHash,
            LocalDateTime after
    );

    // ── Used by TransactionService (idempotency check) ────────────────────────
    @Query("SELECT r.txnId FROM RawTransaction r WHERE r.txnId IN :txnIds")
    List<String> findAllTxnIdsByTxnIdIn(@Param("txnIds") List<String> txnIds);

    // ── Used by HourlyAggregationJob ──────────────────────────────────────────
    @Query(value = """
            SELECT
                DATE(txn_date)      AS txn_date,
                HOUR(txn_date)      AS txn_hour,
                merchant_id,
                payment_mode,
                card_type,
                card_scheme,
                ibibo_code,
                COUNT(*)            AS txn_count,
                SUM(txn_amount)     AS total_txn_amount,
                SUM(mdr_amount)     AS total_mdr_amount
            FROM raw_transactions
            WHERE is_duplicate = FALSE
              AND txn_date >= :windowStart
              AND txn_date <  :windowEnd
            GROUP BY
                DATE(txn_date),
                HOUR(txn_date),
                merchant_id,
                payment_mode,
                card_type,
                card_scheme,
                ibibo_code
            """,
            nativeQuery = true)
    List<Object[]> aggregateForHourlyWindow(
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd")   LocalDateTime windowEnd
    );
}
