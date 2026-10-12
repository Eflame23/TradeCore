package com.tradecore.ledger.service;

import com.tradecore.ledger.model.WalletDTO;
import com.tradecore.risk.model.Side;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);
    private final JdbcTemplate jdbcTemplate;

    public WalletService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public WalletDTO getWallet(String userId) {
        final String effectiveUserId = (userId == null || userId.isBlank()) ? "alice" : userId.trim();

        List<WalletDTO> wallets = jdbcTemplate.query(
                "SELECT user_id, " +
                        "COALESCE(cash_balance, 100000.00) AS cash_balance, " +
                        "COALESCE(asset_balance, 10.00000000) AS asset_balance, " +
                        "COALESCE(reserved_cash, 0.00) AS reserved_cash, " +
                        "COALESCE(reserved_asset, 0.00) AS reserved_asset " +
                        "FROM accounts WHERE user_id = ?",
                (rs, rowNum) -> {
                    BigDecimal cash = rs.getBigDecimal("cash_balance");
                    BigDecimal asset = rs.getBigDecimal("asset_balance");
                    BigDecimal resCash = rs.getBigDecimal("reserved_cash");
                    BigDecimal resAsset = rs.getBigDecimal("reserved_asset");

                    if (cash == null) cash = new BigDecimal("100000.00");
                    if (asset == null) asset = new BigDecimal("10.00000000");
                    if (resCash == null) resCash = BigDecimal.ZERO;
                    if (resAsset == null) resAsset = BigDecimal.ZERO;

                    BigDecimal availCash = cash.subtract(resCash);
                    BigDecimal availAsset = asset.subtract(resAsset);
                    return new WalletDTO(effectiveUserId, cash, asset, resCash, resAsset, availCash, availAsset);
                },
                effectiveUserId
        );

        if (wallets.isEmpty()) {
            try {
                jdbcTemplate.update(
                        "INSERT INTO accounts (user_id, cash_balance, asset_balance, reserved_cash, reserved_asset) " +
                                "SELECT ?, 100000.00, 10.00000000, 0.00, 0.00 " +
                                "WHERE NOT EXISTS (SELECT 1 FROM accounts WHERE user_id = ?)",
                        effectiveUserId, effectiveUserId
                );
            } catch (Exception e) {
                log.warn("Notice: account creation for {}: {}", effectiveUserId, e.getMessage());
            }

            return new WalletDTO(effectiveUserId, new BigDecimal("100000.00"), new BigDecimal("10.00000000"),
                    BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000.00"), new BigDecimal("10.00000000"));
        }

        return wallets.get(0);
    }

    @Transactional
    public boolean reserveFunds(String userId, Side side, BigDecimal price, long qty) {
        WalletDTO wallet = getWallet(userId);
        if (side == Side.BUY) {
            BigDecimal priceVal = (price != null && price.signum() > 0) ? price : new BigDecimal("50000.00");
            BigDecimal requiredCash = priceVal.multiply(BigDecimal.valueOf(qty));
            if (wallet.availableCash().compareTo(requiredCash) < 0) {
                log.warn("Insufficient available cash for user {}. Required: {}, Available: {}",
                        userId, requiredCash, wallet.availableCash());
                return false;
            }
            jdbcTemplate.update(
                    "UPDATE accounts SET reserved_cash = COALESCE(reserved_cash, 0) + ? WHERE user_id = ?",
                    requiredCash, userId
            );
        } else {
            BigDecimal requiredAsset = BigDecimal.valueOf(qty);
            if (wallet.availableAsset().compareTo(requiredAsset) < 0) {
                log.warn("Insufficient available asset for user {}. Required: {}, Available: {}",
                        userId, requiredAsset, wallet.availableAsset());
                return false;
            }
            jdbcTemplate.update(
                    "UPDATE accounts SET reserved_asset = COALESCE(reserved_asset, 0) + ? WHERE user_id = ?",
                    requiredAsset, userId
            );
        }
        return true;
    }

    @Transactional
    public void unreserveFunds(String userId, Side side, BigDecimal price, long qty) {
        if (side == Side.BUY) {
            BigDecimal priceVal = (price != null && price.signum() > 0) ? price : BigDecimal.ZERO;
            BigDecimal amount = priceVal.multiply(BigDecimal.valueOf(qty));
            jdbcTemplate.update(
                    "UPDATE accounts SET reserved_cash = CASE WHEN COALESCE(reserved_cash, 0) - ? < 0 THEN 0 ELSE COALESCE(reserved_cash, 0) - ? END WHERE user_id = ?",
                    amount, amount, userId
            );
        } else {
            BigDecimal amount = BigDecimal.valueOf(qty);
            jdbcTemplate.update(
                    "UPDATE accounts SET reserved_asset = CASE WHEN COALESCE(reserved_asset, 0) - ? < 0 THEN 0 ELSE COALESCE(reserved_asset, 0) - ? END WHERE user_id = ?",
                    amount, amount, userId
            );
        }
    }

    @Transactional
    public WalletDTO deposit(String userId, BigDecimal cashAmount, BigDecimal assetAmount) {
        getWallet(userId); // Ensure account exists
        jdbcTemplate.update(
                "UPDATE accounts SET cash_balance = COALESCE(cash_balance, 0) + ?, asset_balance = COALESCE(asset_balance, 0) + ? WHERE user_id = ?",
                cashAmount != null ? cashAmount : BigDecimal.ZERO,
                assetAmount != null ? assetAmount : BigDecimal.ZERO,
                userId
        );
        return getWallet(userId);
    }
}
