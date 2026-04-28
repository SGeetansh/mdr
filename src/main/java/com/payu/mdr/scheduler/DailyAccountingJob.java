package com.payu.mdr.scheduler;

import com.payu.mdr.repository.DailyAccountingRepository;
import com.payu.mdr.repository.DailyMdrAggRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyAccountingJob {

    private final DailyMdrAggRepository dailyMdrAggRepository;
    private final DailyAccountingRepository dailyAccountingRepository;

    /**
     * Runs once a day at 01:00 AM.
     *
     * Why 01:00 AM and not midnight?
     * The last HourlyAggregationJob of the day runs at 00:00 (midnight),
     * aggregating the 23:xx transactions from the previous day.
     * We wait until 01:00 to ensure that midnight hourly job has completed
     * before we roll up daily_mdr_agg into daily_accounting.
     *
     * Window: yesterday's date (LocalDate.now().minusDays(1))
     *
     * Why yesterday and not today?
     * At 01:00 AM, "today" (the current date) has only 1 hour of data.
     * "Yesterday" is the fully closed day — all 24 hourly slots are committed.
     * Rolling up a fully closed day guarantees complete and correct totals.
     *
     * Cron: "0 0 1 * * *" = second=0, minute=0, hour=1, every day.
     */
    @Scheduled(cron = "0 0 1 * * *")
    @Transactional
    public void runDailyAccounting() {
        LocalDate yesterday = LocalDate.now().minusDays(1);

        log.info("DailyAccountingJob starting — rolling up date [{}]", yesterday);

        try {
            // Step 1 — aggregate daily_mdr_agg rows for yesterday.
            //          Groups by merchant_id, summing across all payment dimensions
            //          (payment_mode, card_type, card_scheme, ibibo_code).
            //          Returns List<Object[]> — see DailyMdrAggRepository for projection.
            var aggregations = dailyMdrAggRepository.aggregateForDate(yesterday);

            if (aggregations.isEmpty()) {
                log.info("DailyAccountingJob — no data for [{}], nothing to upsert.", yesterday);
                return;
            }

            int upsertCount = 0;

            // Step 2 — upsert each merchant's daily total into daily_accounting.
            //          Uses ON DUPLICATE KEY UPDATE keyed on (txn_date, merchant_id).
            //          Running the job twice for the same date is safe — totals are
            //          replaced, not accumulated.
            for (Object[] row : aggregations) {
                // Projection order matches SELECT in aggregateForDate:
                // [0] txn_date           (java.sql.Date)
                // [1] merchant_id        (String)
                // [2] txn_count          (Long)
                // [3] gross_txn_amount   (BigDecimal)
                // [4] total_mdr_amount   (BigDecimal)

                LocalDate txnDate     = ((java.sql.Date) row[0]).toLocalDate();
                String merchantId     = (String) row[1];
                long txnCount         = ((Number) row[2]).longValue();
                BigDecimal grossAmount = (BigDecimal) row[3];
                BigDecimal mdrAmount   = (BigDecimal) row[4];

                // net_settlement_amount = gross - mdr
                // This is the amount the merchant actually receives after fees.
                BigDecimal netSettlement = grossAmount.subtract(mdrAmount);

                dailyAccountingRepository.upsertAccounting(
                        txnDate,
                        merchantId,
                        txnCount,
                        grossAmount,
                        mdrAmount,
                        netSettlement
                );
                upsertCount++;
            }

            log.info("DailyAccountingJob complete — {} merchant(s) upserted for date [{}]",
                    upsertCount, yesterday);

        } catch (Exception e) {
            log.error("DailyAccountingJob failed for date [{}]: {}",
                    yesterday, e.getMessage(), e);
            throw e;
        }
    }
}