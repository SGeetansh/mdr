# MDR Pricing Engine

This project implements a rule-based MDR pricing pipeline for payment transactions using Java 17, Spring Boot 3, and MySQL.

The system accepts transaction batches, applies idempotency and deduplication checks, calculates MDR using configurable database rules, stores raw transaction facts, and rolls them up into hourly, daily, and accounting aggregates.

## Tech Stack

- Java 17
- Spring Boot 3.5.x
- Spring Web
- Spring Validation
- Spring Data JPA
- MySQL 8
- Docker / Docker Compose
- JUnit 5

## Problem Statement

The assignment models a payment-gateway MDR engine.

For each incoming payment transaction, the platform needs to:

1. accept the transaction batch safely
2. avoid double processing on retries
3. identify likely duplicates
4. select the correct MDR rule
5. calculate the MDR amount
6. preserve raw traceability
7. aggregate data for operational and accounting use

At scale, the assignment asks us to think about traffic moving from roughly `50 lakh` transactions per day toward `5 crore` transactions per day, so both correctness and operational scalability matter.


![flow diagram](flow.png)

To start the repo, just docker compose up. 

## API

### Ingestion endpoint

```text
POST /api/v1/transactions/batch
```

### curl

From inside the app container:

```bash
docker compose exec -T app curl -sS -i \
  -H "Content-Type: application/json" \
  --data-binary @samples/batch-ingestion-sample.json \
  http://localhost:8080/api/v1/transactions/batch
```

From the host machine, if the server is running and port `8080` is exposed:

```bash
curl -sS -i \
  -H "Content-Type: application/json" \
  --data-binary @samples/batch-ingestion-sample.json \
  http://localhost:8080/api/v1/transactions/batch
```

### httpie

```bash
http POST :8080/api/v1/transactions/batch < samples/batch-ingestion-sample.json
```

### Expected success response

```json
{
  "totalReceived": 6,
  "duplicatesFound": 1,
  "successfullyPriced": 5,
  "batchId": "SAMPLE-BATCH-001",
  "message": "Batch processed successfully"
}
```

## Inspecting Stored Data

Check raw rows for the sample batch:

```bash
docker compose exec -T db mysql -u payu -ppayu payu -e "
SELECT
  txn_id,
  merchant_id,
  payment_mode,
  card_scheme,
  txn_amount,
  mdr_amount,
  rule_id,
  is_duplicate,
  trace_key
FROM raw_transactions
WHERE batch_id = 'SAMPLE-BATCH-001'
ORDER BY txn_id;
"
```

Useful cleanup command:

```bash
docker compose exec -T db mysql -u payu -ppayu payu -e "
DELETE FROM raw_transactions WHERE batch_id = 'SAMPLE-BATCH-001';
"
```

## Tests

Run the test suite inside the app container:

```bash
docker compose exec -T app mvn clean test
```




## Design Decisions

### 1. Aurora & MySQL Design

The raw_transactions table is partitioned by DATE(txn_date). This means when the hourly aggregation job queries "give me all transactions from the last hour", MySQL only scans today's partition rather than the entire table. This is called partition pruning and is the single biggest performance lever at high volume.
For indexes, I chose a composite index on (merchant_id, txn_date) as the primary lookup pattern, most operational queries are scoped to a merchant within a time window. A separate index on dedup_key_hash exists solely for deduplication lookups, which never join with merchant or date. The batch_id index supports idempotency checks. Each index is single-purpose: no index tries to serve two query patterns at once.

For clustering in Aurora MySQL, the primary key on raw_transactions is a UUID string. 
In a production Aurora deployment I would switch this to a BIGINT AUTO_INCREMENT or a time-prefixed ID (like ULID) so that inserts are always appended to the end of the B-tree index rather than causing page splits across random positions. Random UUID primary keys are the most common cause of write slowdown on high-volume MySQL tables.

Scaling from 50L to 5Cr rows/day: At 50L rows/day the above design is sufficient. At 5Cr rows/day (approximately 580 writes/second at peak) I would introduce three changes. First, a Kafka layer in front of MySQL — transactions are acknowledged into Kafka immediately (sub-millisecond), then a consumer fleet batches writes to Aurora. This decouples ingestion latency from DB write throughput. Second, sharding by merchant_id — hashing the merchant ID to one of N Aurora shards means each shard handles a fraction of total write load and the shards scale independently. Third, a tiered storage strategy — raw transaction rows older than 7 days are archived to S3 as Parquet files and deleted from MySQL. At 5Cr rows/day, keeping 90 days of raw data in MySQL means 450Cr rows in a single table. Keeping only 7 days (35Cr rows) makes the hot table manageable while cold data remains queryable via Athena for audits.


### 2. Pricing Mode

I chose online pricing with an in-memory rule cache. MDR is calculated synchronously before the transaction is saved, so raw_transactions always has a populated mdr_amount.

The alternative, offline pricing (save first, price later in a batch job), was rejected because it creates a window where rows in raw_transactions have null mdr_amount. Any downstream system reading those rows during that window gets incomplete data. For an MDR engine, the priced transaction is the output of ingestion, there is no valid intermediate state.

The concern with online pricing is DB read latency per transaction. I avoided this by loading all active, date-valid rules into a ConcurrentHashMap in memory once per batch rather than querying the DB for each transaction. Rule lookup is then a pure in-memory operation — microseconds, not milliseconds. The cache is invalidated when a rule is updated or deactivated via a @CacheEvict call, triggering a fresh load from DB. Since rule changes happen a few times a week at most, not per-transaction, this approach gives online pricing with zero per-transaction DB overhead.

### 3. Dedup Strategy
The approach: I compute a SHA-256 hash of merchant_id + DATE(txn_date) + payment_mode + txn_amount and store it as dedup_key_hash on every row. On ingestion, before pricing, I query: does a non-duplicate row with this hash exist with created_at within the last 24 hours? If yes, the incoming transaction is flagged is_duplicate=true and mdr_amount=0. It is still saved to raw_transactions for auditability, it is excluded from aggregation and accounting.

Pros: Simple with no additional infrastructure. One indexed DB lookup per transaction. 100% accurate; no false positives. The 24-hour window is configurable via mdr.dedup.window-hours in application.yml. Every decision is auditable, dedup_key_hash and is_duplicate are stored on the row.

Cons: At 50L transactions/day, every ingestion triggers one SELECT before the INSERT — approximately 58 DB reads/second on average. With the idx_dedup_hash index this is fast, but it does add load. At 5Cr/day (580 reads/second just for dedup) this becomes the bottleneck.

Scale path to 5Cr+: Replace the DB lookup with a Redis Bloom Filter. On ingestion, call BF.EXISTS key hash — returns in under 1ms with no DB involvement. If not seen, call BF.ADD and proceed. The Bloom Filter key has a 24-hour TTL matching the dedup window. At 580 operations/second Redis handles this trivially. The tradeoff is a roughly 0.1% false positive rate — a unique transaction occasionally wrongly flagged as duplicate. For MDR pricing this is acceptable. For hard fraud blocking it would not be.

### 4. Data Drift & Rules
Rule bugs are silent. If the scoring logic picks the wrong rule, every transaction is mispriced in the same direction and nobody notices until settlement reconciliation weeks later. I address this with three detection layers.

Layer 1 - Rule audit log: Every change to mdr_pricing_rules (insert, update, deactivate) writes a record to an mdr_rules_audit table with rule_id, changed_by, changed_at, old_rate, new_rate, and reason. If MDR amounts change unexpectedly on a given date, the audit log for that date immediately shows whether a rule change caused it.

Layer 2 — Daily reconciliation query: The daily job computes, for each (merchant_id, payment_mode, card_scheme) combination, the effective MDR rate actually applied: SUM(mdr_amount) / SUM(txn_amount). This is compared against the expected rate from mdr_pricing_rules. If the variance exceeds a configurable threshold (I use 5%), the discrepancy is logged as a warning and flagged for review. This catches silent mispricing within 24 hours.

Layer 3 — Statistical drift alert: For each merchant-payment combination, I track the 7-day rolling average effective MDR rate. If today's effective rate deviates more than 2 standard deviations from the rolling average, an alert is raised. This catches gradual drift — where a rule bug consistently mischarges in the same direction rather than producing a sudden spike that Layer 2 would catch.

Rollback: Because raw_transactions stores rule_id on every row, I can always re-run MDR calculation for any date range by joining against corrected rules. The original mdr_amount is preserved. A corrected_mdr_amount column holds the reprocessed value, so both the original and corrected figures are available for reconciliation.


### 5. Optional ML — Model Versioning and Rollback
The ML service is a separate Python FastAPI container running an Isolation Forest model. I chose Isolation Forest specifically because it is an unsupervised anomaly detection algorithm — it learns what "normal" transaction patterns look like and flags outliers, requiring no labeled fraud data. Features used: txn_amount, hour_of_day, day_of_week, merchant_txn_velocity_24h, and amount_vs_merchant_avg_ratio.
Versioning: Every trained model is saved as model_v{N}.pkl where N is a monotonically incrementing integer. The FastAPI service loads whichever version is pointed to by the ACTIVE_MODEL_VERSION environment variable. Changing this variable and restarting the container is a complete rollback — it takes under 30 seconds and requires no code change.

Why re-scoring 50L+ rows is never needed: The ML output — risk_score and should_review — is stored in separate columns on raw_transactions and is deliberately never used in any financial calculation. MDR amounts are determined solely by the rule engine. ML only informs human review queues. This means a bad model version causes some transactions to be wrongly flagged for review, but it causes zero financial impact. The fix is to tell the review queue "ignore all flags from model_version=3 after timestamp T." No rows need updating, no data needs replaying.

Retraining cadence: Weekly, on the last 30 days of transactions. If the new model's anomaly rate on a holdout set differs by more than 20% from the previous model, the new version is rejected automatically and the previous version stays active.

### 6. Idempotency
Re-sending the same batch must not re-price or double-count. I enforce this at two levels.
Row-level idempotency via txn_id: The txn_id column has a UNIQUE KEY constraint in the schema. If the same txn_id arrives in two different batches, the second insert fails the unique constraint and is rejected before dedup or pricing runs. This is the primary idempotency guard.
Dedup as a secondary safety net: Even if a client sends the same transaction with a different txn_id (which can happen with buggy retry logic), the SHA-256 dedup hash catches it. The second transaction is saved as is_duplicate=true with mdr_amount=0 and is excluded from all aggregation jobs. It cannot cause double-counting in daily_mdr_agg or daily_accounting because both jobs filter on is_duplicate=false.
What "double-count" means concretely: Without idempotency, a merchant pays MDR twice on the same transaction. daily_accounting shows double the correct total_mdr_amount. The merchant disputes, PayU refunds the difference, reputational damage is done. With both guards in place, the second submission either fails the unique constraint or is saved as a duplicate — in neither case does it affect accounting.

### 8. Docker
The entire system starts with a single command:
`bashdocker-compose up`

This starts rwo containers in dependency order: mysql:8 starts first, Spring Boot waits for the MySQL healthcheck to pass before starting (configured via depends_on: condition: service_healthy).

On first boot, MySQL automatically runs sql/schema.sql and sql/seed_data.sql from the docker-entrypoint-initdb.d mount. This creates all four tables and inserts the baseline MDR pricing rules. No manual setup is required.

### 9. Testing
The test suite covers three layers: unit tests for core logic, and one integration test for the full API flow.
MdrRuleEngineTest (6 tests): Tests the rule scoring and MDR calculation in isolation using Mockito to mock the repository. Covers: merchant-specific rule winning over a generic rule, generic rule applying when no merchant-specific rule exists, fallback to the catch-all default rule, expired rules never being selected, zero rules returning a zero MDR amount, and MDR amount precision (1.85% of 1234.56 = 22.84, correctly rounded with HALF_UP).

DeduplicationServiceTest (7 tests): Tests hash computation and duplicate detection in isolation. Covers: unique transaction not flagged, same hash within 24-hour window flagged as duplicate, same hash outside the window not flagged, different amounts producing different hashes, different merchants producing different hashes, determinism (same inputs always produce the same hash), and hash length always being exactly 64 characters (SHA-256 = 32 bytes = 64 hex characters).

BatchIngestionIntegrationTest (3 tests): Loads the full Spring Boot context against a real MySQL container. Covers: a batch of 3 transactions where 1 is a deliberate duplicate — verifies is_duplicate=true on the duplicate, correct mdr_amount on the unique transactions, and rule_id populated. Re-sending the same batch — verifies exactly 1 row exists in the DB, not 2. Empty batch — verifies the API returns 200 with zero counts rather than throwing an exception.

