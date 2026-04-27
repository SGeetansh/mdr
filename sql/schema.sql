DROP TABLE IF EXISTS daily_accounting;
DROP TABLE IF EXISTS daily_mdr_agg;
DROP TABLE IF EXISTS raw_transactions;
DROP TABLE IF EXISTS mdr_pricing_rules;

-- =====================================================
-- 1. MDR Pricing Rules
-- =====================================================
CREATE TABLE mdr_pricing_rules (
    rule_id BIGINT AUTO_INCREMENT PRIMARY KEY,

    merchant_id VARCHAR(50) NULL,          -- NULL = default fallback rule
    payment_mode VARCHAR(20) NULL,
    card_type VARCHAR(20) NULL,
    card_scheme VARCHAR(20) NULL,
    ibibo_code VARCHAR(30) NULL,

    effective_from DATE NOT NULL,
    effective_to DATE NOT NULL,

    mdr_rate_percent DECIMAL(5,2) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,

    created_by VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_active_lookup (
        merchant_id,
        is_active,
        payment_mode,
        card_type,
        card_scheme,
        ibibo_code
    ),
    INDEX idx_effective_dates (effective_from, effective_to)
);

-- =====================================================
-- 2. Raw Transactions (source table)
-- =====================================================
CREATE TABLE raw_transactions (
    id VARCHAR(64) PRIMARY KEY,

    txn_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL,
    merchant_id VARCHAR(50) NOT NULL,

    txn_date TIMESTAMP(3) NOT NULL,

    payment_mode VARCHAR(20) NOT NULL,
    card_type VARCHAR(20),
    card_scheme VARCHAR(20),
    ibibo_code VARCHAR(30),

    txn_amount DECIMAL(12,2) NOT NULL,
    mdr_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,

    currency VARCHAR(3) DEFAULT 'INR',

    action VARCHAR(20) DEFAULT 'INIT',
    txn_status VARCHAR(20) DEFAULT 'INIT',

    rule_id BIGINT,
    batch_id VARCHAR(50),

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- dedup
    dedup_key_hash VARCHAR(64),
    is_duplicate BOOLEAN DEFAULT FALSE,

    risk_score DECIMAL(8,4) NULL,
    should_review BOOLEAN DEFAULT FALSE,
    
    -- traceability
    trace_key VARCHAR(255),

    CONSTRAINT fk_raw_rule
        FOREIGN KEY (rule_id)
        REFERENCES mdr_pricing_rules(rule_id),

    UNIQUE KEY uq_txn_id (txn_id),

    INDEX idx_merchant_date (merchant_id, txn_date),
    INDEX idx_batch_id (batch_id),
    INDEX idx_dedup_hash (dedup_key_hash),
    INDEX idx_trace_key (trace_key)
);

-- =====================================================
-- 3. Daily MDR Aggregation
-- =====================================================
CREATE TABLE daily_mdr_agg (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    txn_date DATE NOT NULL,
    merchant_id VARCHAR(50) NOT NULL,

    payment_mode VARCHAR(20),
    card_type VARCHAR(20),
    card_scheme VARCHAR(20),
    ibibo_code VARCHAR(30),

    txn_count BIGINT NOT NULL DEFAULT 0,
    total_txn_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    total_mdr_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uq_daily_rollup (
        txn_date,
        merchant_id,
        payment_mode,
        card_type,
        card_scheme,
        ibibo_code
    ),

    INDEX idx_daily_merchant (merchant_id, txn_date)
);

-- =====================================================
-- 4. Daily Accounting
-- =====================================================
CREATE TABLE daily_accounting (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,

    txn_date DATE NOT NULL,
    merchant_id VARCHAR(50) NOT NULL,

    txn_count BIGINT NOT NULL DEFAULT 0,
    gross_txn_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    total_mdr_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    net_settlement_amount DECIMAL(18,2) NOT NULL DEFAULT 0.00,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uq_accounting (txn_date, merchant_id),

    INDEX idx_accounting_lookup (merchant_id, txn_date)
);
