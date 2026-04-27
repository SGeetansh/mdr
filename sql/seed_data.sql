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