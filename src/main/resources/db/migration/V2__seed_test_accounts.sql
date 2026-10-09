-- Seed test accounts only if they do not already exist (ON CONFLICT DO NOTHING)
-- Alice: USD 10,000 and BTC 5
-- Bob: USD 2,000 and BTC 10
INSERT INTO accounts (user_id, cash_balance, asset_balance)
VALUES
    ('alice', 10000.00000000, 5.00000000),
    ('bob', 2000.00000000, 10.00000000)
ON CONFLICT (user_id) DO NOTHING;
