INSERT INTO mdr_pricing_rules (
    merchant_id,
    payment_mode,
    card_type,
    card_scheme,
    ibibo_code,
    effective_from,
    effective_to,
    mdr_rate_percent,
    is_active,
    created_by
)
VALUES
-- Merchant specific best-fit rule
('22137', 'CC', 'CREDIT', 'VISA', 'cc',
 '2026-01-01', '2026-12-31', 1.80, TRUE, 'system'),

-- Merchant specific Rupay debit
('22137', 'CC', 'DEBIT', 'RUPAY', 'cc',
 '2026-01-01', '2026-12-31', 0.90, TRUE, 'system'),

-- Generic UPI rule
(NULL, 'UPI', NULL, NULL, NULL,
 '2026-01-01', '2026-12-31', 0.25, TRUE, 'system'),

-- Generic card fallback
(NULL, 'CC', NULL, NULL, NULL,
 '2026-01-01', '2026-12-31', 2.00, TRUE, 'system'),

-- Full default fallback
(NULL, NULL, NULL, NULL, NULL,
 '2026-01-01', '2026-12-31', 2.50, TRUE, 'system');

-- Seed Data for MDR Rule testing
-- Default catch-all rule (all NULLs = matches everything)
INSERT INTO mdr_pricing_rules
    (merchant_id, payment_mode, card_type, card_scheme, ibibo_code,
     effective_from, effective_to, mdr_rate_percent, is_active, created_by)
VALUES
    (NULL, NULL, NULL, NULL, NULL,
     '2025-01-01', '2027-12-31', 2.00, true, 'system');

-- UPI rule
INSERT INTO mdr_pricing_rules
    (merchant_id, payment_mode, card_type, card_scheme, ibibo_code,
     effective_from, effective_to, mdr_rate_percent, is_active, created_by)
VALUES
    (NULL, 'UPI', NULL, NULL, NULL,
     '2025-01-01', '2027-12-31', 0.00, true, 'system');

-- Credit card — Visa
INSERT INTO mdr_pricing_rules
    (merchant_id, payment_mode, card_type, card_scheme, ibibo_code,
     effective_from, effective_to, mdr_rate_percent, is_active, created_by)
VALUES
    (NULL, 'CC', 'CREDIT', 'VISA', NULL,
     '2025-01-01', '2027-12-31', 1.80, true, 'system');

-- Credit card — RuPay
INSERT INTO mdr_pricing_rules
    (merchant_id, payment_mode, card_type, card_scheme, ibibo_code,
     effective_from, effective_to, mdr_rate_percent, is_active, created_by)
VALUES
    (NULL, 'CC', 'CREDIT', 'RUPAY', NULL,
     '2025-01-01', '2027-12-31', 0.00, true, 'system');

-- Netbanking — SBI
INSERT INTO mdr_pricing_rules
    (merchant_id, payment_mode, card_type, card_scheme, ibibo_code,
     effective_from, effective_to, mdr_rate_percent, is_active, created_by)
VALUES
    (NULL, 'NB', NULL, NULL, 'SBIB',
     '2025-01-01', '2027-12-31', 0.90, true, 'system');

-- Netbanking — HDFC
INSERT INTO mdr_pricing_rules
    (merchant_id, payment_mode, card_type, card_scheme, ibibo_code,
     effective_from, effective_to, mdr_rate_percent, is_active, created_by)
VALUES
    (NULL, 'NB', NULL, NULL, 'HDFCNB',
     '2025-01-01', '2027-12-31', 1.10, true, 'system');

-- Merchant-specific rule for merchant 22137 — CC Visa gets a better rate
INSERT INTO mdr_pricing_rules
    (merchant_id, payment_mode, card_type, card_scheme, ibibo_code,
     effective_from, effective_to, mdr_rate_percent, is_active, created_by)
VALUES
    ('22137', 'CC', 'CREDIT', 'VISA', NULL,
     '2025-01-01', '2027-12-31', 1.50, true, 'system');

-- Expired rule — should never be picked
INSERT INTO mdr_pricing_rules
    (merchant_id, payment_mode, card_type, card_scheme, ibibo_code,
     effective_from, effective_to, mdr_rate_percent, is_active, created_by)
VALUES
    (NULL, 'CC', 'CREDIT', 'MASTERCARD', NULL,
     '2020-01-01', '2021-12-31', 3.00, true, 'system');