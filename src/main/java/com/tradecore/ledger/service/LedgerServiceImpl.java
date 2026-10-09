package com.tradecore.ledger.service;

import com.tradecore.ledger.exception.AccountNotFoundException;
import com.tradecore.ledger.exception.InsufficientBalanceException;
import com.tradecore.ledger.exception.TradeValidationException;
import com.tradecore.ledger.model.Account;
import com.tradecore.ledger.model.Trade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Implementation of LedgerService utilizing Spring JDBC and transaction management.
 */
@Service
public class LedgerServiceImpl implements LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerServiceImpl.class);

    private final JdbcTemplate jdbcTemplate;

    public LedgerServiceImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
    public boolean settle(Trade trade) {
        // 1 & 2. Validate trade fields and ensure buyer != seller
        validateTrade(trade);

        // 3. Calculate trade value: price * qty
        BigDecimal tradeQty = BigDecimal.valueOf(trade.qty());
        BigDecimal totalUsdValue = trade.price().multiply(tradeQty);

        // 4 & 5. Claim the trade ID atomically using PostgreSQL ON CONFLICT DO NOTHING
        // Return false if trade has already been settled (idempotency check)
        int inserted = jdbcTemplate.update(
                "INSERT INTO settlements (trade_id, settled_at) VALUES (?, CURRENT_TIMESTAMP) ON CONFLICT (trade_id) DO NOTHING",
                trade.tradeId()
        );

        if (inserted == 0) {
            log.info("Trade {} has already been settled. Skipping settlement (idempotent no-op).", trade.tradeId());
            return false;
        }

        // 6. Lock both account rows in a deterministic consistent order using SELECT ... FOR UPDATE to prevent deadlocks
        String firstUser = trade.buyerId().compareTo(trade.sellerId()) < 0 ? trade.buyerId() : trade.sellerId();
        String secondUser = trade.buyerId().compareTo(trade.sellerId()) < 0 ? trade.sellerId() : trade.buyerId();

        Account firstAccount = lockAccount(firstUser);
        Account secondAccount = lockAccount(secondUser);

        // 7. Verify both accounts exist
        if (firstAccount == null) {
            throw new AccountNotFoundException("Account not found for user: " + firstUser);
        }
        if (secondAccount == null) {
            throw new AccountNotFoundException("Account not found for user: " + secondUser);
        }

        Account buyerAccount = trade.buyerId().equals(firstUser) ? firstAccount : secondAccount;
        Account sellerAccount = trade.sellerId().equals(firstUser) ? firstAccount : secondAccount;

        // 8. Verify buyer has sufficient USD and seller has sufficient BTC
        if (buyerAccount.cashBalance().compareTo(totalUsdValue) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Buyer '%s' has insufficient USD. Required: %s, Available: %s",
                            trade.buyerId(), totalUsdValue, buyerAccount.cashBalance())
            );
        }

        if (sellerAccount.assetBalance().compareTo(tradeQty) < 0) {
            throw new InsufficientBalanceException(
                    String.format("Seller '%s' has insufficient BTC. Required: %s, Available: %s",
                            trade.sellerId(), tradeQty, sellerAccount.assetBalance())
            );
        }

        // 9 & 11. Update buyer: Debit USD, Credit BTC
        int buyerUpdated = jdbcTemplate.update(
                "UPDATE accounts SET cash_balance = cash_balance - ?, asset_balance = asset_balance + ? WHERE user_id = ?",
                totalUsdValue, tradeQty, trade.buyerId()
        );
        if (buyerUpdated != 1) {
            throw new IllegalStateException("Failed to update balances for buyer: " + trade.buyerId());
        }

        // 10 & 12. Update seller: Credit USD, Debit BTC
        int sellerUpdated = jdbcTemplate.update(
                "UPDATE accounts SET cash_balance = cash_balance + ?, asset_balance = asset_balance - ? WHERE user_id = ?",
                totalUsdValue, tradeQty, trade.sellerId()
        );
        if (sellerUpdated != 1) {
            throw new IllegalStateException("Failed to update balances for seller: " + trade.sellerId());
        }

        // 13. Insert exactly four ledger entries
        // 1) Buyer USD debit (-totalUsdValue)
        // 2) Seller USD credit (+totalUsdValue)
        // 3) Seller BTC debit (-tradeQty)
        // 4) Buyer BTC credit (+tradeQty)
        jdbcTemplate.batchUpdate(
                "INSERT INTO ledger_entries (trade_id, user_id, currency, amount, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                List.of(
                        new Object[]{trade.tradeId(), trade.buyerId(), "USD", totalUsdValue.negate()},
                        new Object[]{trade.tradeId(), trade.sellerId(), "USD", totalUsdValue},
                        new Object[]{trade.tradeId(), trade.sellerId(), "BTC", tradeQty.negate()},
                        new Object[]{trade.tradeId(), trade.buyerId(), "BTC", tradeQty}
                )
        );

        log.info("Successfully settled trade {} between buyer {} and seller {} for {} BTC at ${}",
                trade.tradeId(), trade.buyerId(), trade.sellerId(), tradeQty, trade.price());
        return true;
    }

    private void validateTrade(Trade trade) {
        if (trade == null) {
            throw new TradeValidationException("Trade cannot be null");
        }
        if (trade.tradeId() == null || trade.tradeId().isBlank()) {
            throw new TradeValidationException("Trade ID cannot be null or blank");
        }
        if (trade.buyerId() == null || trade.buyerId().isBlank()) {
            throw new TradeValidationException("Buyer ID cannot be null or blank");
        }
        if (trade.sellerId() == null || trade.sellerId().isBlank()) {
            throw new TradeValidationException("Seller ID cannot be null or blank");
        }
        if (trade.buyerId().equalsIgnoreCase(trade.sellerId())) {
            throw new TradeValidationException("Self-trading is not permitted. Buyer and seller are identical: " + trade.buyerId());
        }
        if (trade.price() == null || trade.price().compareTo(BigDecimal.ZERO) <= 0) {
            throw new TradeValidationException("Trade price must be positive. Provided: " + trade.price());
        }
        if (trade.qty() <= 0) {
            throw new TradeValidationException("Trade quantity must be positive. Provided: " + trade.qty());
        }
    }

    private Account lockAccount(String userId) {
        List<Account> accounts = jdbcTemplate.query(
                "SELECT user_id, cash_balance, asset_balance FROM accounts WHERE user_id = ? FOR UPDATE",
                (rs, rowNum) -> new Account(
                        rs.getString("user_id"),
                        rs.getBigDecimal("cash_balance"),
                        rs.getBigDecimal("asset_balance")
                ),
                userId
        );
        return accounts.isEmpty() ? null : accounts.get(0);
    }
}
