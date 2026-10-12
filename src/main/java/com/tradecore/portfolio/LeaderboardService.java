package com.tradecore.portfolio;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class LeaderboardService {

    private final JdbcTemplate jdbcTemplate;
    private final PortfolioService portfolioService;

    public LeaderboardService(JdbcTemplate jdbcTemplate, PortfolioService portfolioService) {
        this.jdbcTemplate = jdbcTemplate;
        this.portfolioService = portfolioService;
    }

    public List<LeaderboardEntry> getLeaderboard() {
        List<String> userIds = jdbcTemplate.query(
                "SELECT user_id FROM accounts",
                (rs, rowNum) -> rs.getString("user_id")
        );

        BigDecimal marketPrice = portfolioService.getMarketPrice();
        List<LeaderboardEntry> entries = new ArrayList<>();

        for (String userId : userIds) {
            PortfolioDTO portfolio = portfolioService.getPortfolio(userId);
            entries.add(new LeaderboardEntry(
                    0,
                    userId,
                    portfolio.totalEquity(),
                    portfolio.cashBalance(),
                    portfolio.assetBalance(),
                    portfolio.pnlAmount()
            ));
        }

        // Sort descending by total equity
        entries.sort(Comparator.comparing(LeaderboardEntry::totalEquity).reversed());

        // Assign ranks
        List<LeaderboardEntry> ranked = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry e = entries.get(i);
            ranked.add(new LeaderboardEntry(
                    i + 1,
                    e.userId(),
                    e.totalEquity().setScale(2, RoundingMode.HALF_UP),
                    e.cashBalance().setScale(2, RoundingMode.HALF_UP),
                    e.assetBalance().setScale(4, RoundingMode.HALF_UP),
                    e.pnlAmount().setScale(2, RoundingMode.HALF_UP)
            ));
        }

        return ranked;
    }
}
