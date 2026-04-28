package com.payu.mdr.scheduler;

import com.payu.mdr.repository.HourlyMdrAggRepository;
import com.payu.mdr.repository.RawTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class HourlyAggregationJob {

    private final RawTransactionRepository rawTransactionRepository;
    private final HourlyMdrAggRepository hourlyMdrAggRepository;

    /**
     * Runs at the top of every hour (e.g., 01:00, 02:00 ...).
     *
     * Window: [now - 2 hours, now - 1 hour)
     *
     * Why a one-hour-ago window instead of "the last 60 minutes"?
     * If the job fires at 02:00:00, we aggregate the 01:xx:xx transactions
     * — the hour that is now fully closed. Transactions from 01:00:00 to
     * 01:59:59 are guaranteed committed; nothing new can arrive for that
     * window. Using "last 60 minutes" would catch some of the current
     * (still-open) hour and miss late-arriving rows from the closed hour.
     *
     * Cron: "0 0 * * * *" = second=0, minute=0, every hour, every day.
     */
    @Scheduled(cron = "0 0 * * * *")
    // @Scheduled(cron = "*/15 * * * * *")
    @Transactional
    public void runHourlyAggregation() {
        LocalDateTime windowEnd   = LocalDateTime
                                    .now()
                                    .withMinute(0)
                                    .withSecond(0)
                                    .withNano(0);

        LocalDateTime windowStart = windowEnd.minusHours(1);

        // LocalDateTime windowStart =
        // LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);

        // LocalDateTime windowEnd = windowStart.plusHours(1);

        log.info("HourlyAggregationJob starting — window [{} → {}]", windowStart, windowEnd);

        try {
            // Step 1 — fetch aggregated rows for the closed hour from raw_transactions.
            //          Groups by merchant_id, payment_mode, card_type, card_scheme, ibibo_code.
            //          Only non-duplicate transactions are included.
            //          Returns List<Object[]> — see RawTransactionRepository for projection details.
            var aggregations = rawTransactionRepository
                    .aggregateForHourlyWindow(windowStart, windowEnd);

            if (aggregations.isEmpty()) {
                log.info("HourlyAggregationJob — no transactions in window, nothing to upsert.");
                return;
            }

            int upsertCount = 0;

            // Step 2 — upsert each aggregation group into hourly_mdr_agg.
            //          Uses MySQL ON DUPLICATE KEY UPDATE keyed on
            //          (window_start, merchant_id, payment_mode, card_type, card_scheme, ibibo_code).
            //          Running the job twice for the same hour is safe — amounts are
            //          replaced, not double-counted (see repository for UPDATE clause).
            for (Object[] row : aggregations) {
                // Projection order matches the SELECT in aggregateForHourlyWindow:
                // [0] txn_date      (java.sql.Date — cast to LocalDate via .toLocalDate())
                // [1] txn_hour      (Number)
                // [2] merchant_id   (String)
                // [3] payment_mode  (String)
                // [4] card_type     (String, nullable)
                // [5] card_scheme   (String, nullable)
                // [6] ibibo_code    (String, nullable)
                // [7] txn_count     (Long)
                // [8] total_txn_amount (BigDecimal)
                // [9] total_mdr_amount (BigDecimal)

                hourlyMdrAggRepository.upsertHourlyAggregation(
                        /* windowStart      */ windowStart,
                        /* windowEnd        */ windowEnd,
                        /* txnDate          */ ((java.sql.Date) row[0]).toLocalDate(),
                        /* txnHour          */ ((Number) row[1]).intValue(),
                        /* merchantId       */ (String) row[2],
                        /* paymentMode      */ (String) row[3],
                        /* cardType         */ (String) row[4],
                        /* cardScheme       */ (String) row[5],
                        /* ibiboCode        */ (String) row[6],
                        /* txnCount         */ ((Number) row[7]).longValue(),
                        /* totalTxnAmount   */ (java.math.BigDecimal) row[8],
                        /* totalMdrAmount   */ (java.math.BigDecimal) row[9]
                );
                upsertCount++;
            }

            log.info("HourlyAggregationJob complete — {} group(s) upserted for window [{} → {}]",
                    upsertCount, windowStart, windowEnd);

        } catch (Exception e) {
            // Log and rethrow so Spring marks the transaction for rollback
            // and the failure surfaces in monitoring / alerting.
            log.error("HourlyAggregationJob failed for window [{} → {}]: {}",
                    windowStart, windowEnd, e.getMessage(), e);
            throw e;
        }
    }
}
