package com.tradecore.portfolio;

import com.tradecore.ledger.model.WalletDTO;
import com.tradecore.ledger.service.WalletService;
import com.trading.engine.MatchingEngine;
import com.trading.engine.OrderBookMatchingEngine;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class PortfolioService {

    private final WalletService walletService;
    private final JdbcTemplate jdbcTemplate;
    private final MatchingEngine matchingEngine;

    public PortfolioService(WalletService walletService, JdbcTemplate jdbcTemplate, MatchingEngine matchingEngine) {
        this.walletService = walletService;
        this.jdbcTemplate = jdbcTemplate;
        this.matchingEngine = matchingEngine;
    }

    public BigDecimal getMarketPrice() {
        if (matchingEngine instanceof OrderBookMatchingEngine engine) {
            var snapshot = engine.snapshot();
            if (!snapshot.asks().isEmpty() && !snapshot.bids().isEmpty()) {
                BigDecimal bestAsk = snapshot.asks().get(0).price();
                BigDecimal bestBid = snapshot.bids().get(0).price();
                return bestAsk.add(bestBid).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            } else if (!snapshot.asks().isEmpty()) {
                return snapshot.asks().get(0).price();
            } else if (!snapshot.bids().isEmpty()) {
                return snapshot.bids().get(0).price();
            }
        }

        // Query last trade price from database
        List<BigDecimal> lastPrices = jdbcTemplate.query(
                "SELECT price FROM trade_audits ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> rs.getBigDecimal("price")
        );

        return lastPrices.isEmpty() ? new BigDecimal("50000.00") : lastPrices.get(0);
    }

    public PortfolioDTO getPortfolio(String userId) {
        WalletDTO wallet = walletService.getWallet(userId);
        BigDecimal marketPrice = getMarketPrice();
        BigDecimal assetValue = wallet.assetBalance().multiply(marketPrice);
        BigDecimal totalEquity = wallet.cashBalance().add(assetValue);

        BigDecimal initialBalance = new BigDecimal("10000.00").add(new BigDecimal("1.00").multiply(marketPrice));
        BigDecimal pnlAmount = totalEquity.subtract(initialBalance);
        BigDecimal pnlPercentage = initialBalance.signum() > 0
                ? pnlAmount.divide(initialBalance, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100"))
                : BigDecimal.ZERO;

        return new PortfolioDTO(
                userId,
                wallet.cashBalance(),
                wallet.assetBalance(),
                marketPrice,
                assetValue,
                totalEquity,
                pnlAmount,
                pnlPercentage
        );
    }
}
