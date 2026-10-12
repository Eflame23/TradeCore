-- Seed test accounts using standard ANSI SQL
INSERT INTO accounts (user_id, cash_balance, asset_balance)
SELECT 'alice', 1000000.00000000, 1000.00000000
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE user_id = 'alice');

INSERT INTO accounts (user_id, cash_balance, asset_balance)
SELECT 'bob', 1000000.00000000, 1000.00000000
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE user_id = 'bob');

INSERT INTO accounts (user_id, cash_balance, asset_balance)
SELECT 'charlie', 1000000.00000000, 1000.00000000
WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE user_id = 'charlie');
