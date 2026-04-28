package com.payu.mdr.repository;

import com.payu.mdr.entity.DailyAccounting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;

@Repository
public interface DailyAccountingRepository extends JpaRepository<DailyAccounting, Long> {

    @Modifying
    @Query(value = """
        INSERT INTO daily_accounting
            (txn_date, merchant_id, txn_count, gross_txn_amount,
             total_mdr_amount, net_settlement_amount)
        VALUES
            (:txnDate, :merchantId, :txnCount, :grossAmount,
             :mdrAmount, :netSettlement)
        ON DUPLICATE KEY UPDATE
            txn_count            = VALUES(txn_count),
            gross_txn_amount     = VALUES(gross_txn_amount),
            total_mdr_amount     = VALUES(total_mdr_amount),
            net_settlement_amount = VALUES(net_settlement_amount),
            updated_at           = CURRENT_TIMESTAMP
        """, nativeQuery = true)
    void upsertAccounting(
            @Param("txnDate")       LocalDate txnDate,
            @Param("merchantId")    String merchantId,
            @Param("txnCount")      long txnCount,
            @Param("grossAmount")   BigDecimal grossAmount,
            @Param("mdrAmount")     BigDecimal mdrAmount,
            @Param("netSettlement") BigDecimal netSettlement
    );
}