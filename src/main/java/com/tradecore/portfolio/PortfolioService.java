package com.tradecore.portfolio;

import com.tradecore.ledger.model.WalletDTO;
import com.tradecore.ledger.service.WalletService;
import com.trading.engine.InstrumentMatchingEngineManager;
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

    public BigDecimal getMarketPrice(String symbol) {
        String sym = (symbol == null || symbol.isBlank()) ? "BTC-USD" : symbol.trim().toUpperCase();
        
        if (matchingEngine instanceof InstrumentMatchingEngineManager manager) {
            var snapshot = manager.snapshot(sym);
            if (!snapshot.asks().isEmpty() && !snapshot.bids().isEmpty()) {
                BigDecimal bestAsk = snapshot.asks().get(0).price();
                BigDecimal bestBid = snapshot.bids().get(0).price();
                return bestAsk.add(bestBid).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            } else if (!snapshot.asks().isEmpty()) {
                return snapshot.asks().get(0).price();
            } else if (!snapshot.bids().isEmpty()) {
                return snapshot.bids().get(0).price();
            }
        } else if (matchingEngine instanceof OrderBookMatchingEngine engine) {
            var snapshot = engine.snapshot();
            if (!snapshot.asks().isEmpty() && !snapshot.bids().isEmpty()) {
                BigDecimal bestAsk = snapshot.asks().get(0).price();
                BigDecimal bestBid = snapshot.bids().get(0).price();
                return bestAsk.add(bestBid).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            }
        }

        // Query last trade price from database
        List<BigDecimal> lastPrices = jdbcTemplate.query(
                "SELECT price FROM trade_audits ORDER BY id DESC LIMIT 1",
                (rs, rowNum) -> rs.getBigDecimal("price")
        );

        return lastPrices.isEmpty() ? new BigDecimal("50250.00") : lastPrices.get(0);
    }

    public BigDecimal getMarketPrice() {
        return getMarketPrice("BTC-USD");
    }

    public PortfolioDTO getPortfolio(String userId) {
        WalletDTO wallet = walletService.getWallet(userId);
        BigDecimal marketPrice = getMarketPrice("BTC-USD");
        BigDecimal assetValue = wallet.assetBalance().multiply(marketPrice);
        BigDecimal totalEquity = wallet.cashBalance().add(assetValue);

        // Standard seed capital baseline ($1,000,000 cash + 10 BTC * market price)
        BigDecimal initialCapital = new BigDecimal("1000000.00").add(new BigDecimal("10.00").multiply(marketPrice));
        BigDecimal pnlAmount = totalEquity.subtract(initialCapital);
        BigDecimal pnlPercentage = initialCapital.signum() > 0
                ? pnlAmount.divide(initialCapital, 6, RoundingMode.HALF_UP).multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new PortfolioDTO(
                userId,
                wallet.cashBalance(),
                wallet.assetBalance(),
                marketPrice,
                assetValue,
                totalEquity,
                initialCapital,
                pnlAmount,
                pnlPercentage
        );
    }
}
