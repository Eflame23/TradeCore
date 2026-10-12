package com.tradecore.portfolio.web;

import com.tradecore.portfolio.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final LeaderboardService leaderboardService;
    private final CandleChartService candleChartService;

    public PortfolioController(PortfolioService portfolioService,
                               LeaderboardService leaderboardService,
                               CandleChartService candleChartService) {
        this.portfolioService = portfolioService;
        this.leaderboardService = leaderboardService;
        this.candleChartService = candleChartService;
    }

    @GetMapping("/portfolio/{userId}")
    public ResponseEntity<PortfolioDTO> getPortfolio(@PathVariable String userId) {
        return ResponseEntity.ok(portfolioService.getPortfolio(userId));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<LeaderboardEntry>> getLeaderboard() {
        return ResponseEntity.ok(leaderboardService.getLeaderboard());
    }

    @GetMapping("/chart/candles")
    public ResponseEntity<List<Candle>> getCandles(@RequestParam(defaultValue = "1") int intervalMinutes) {
        return ResponseEntity.ok(candleChartService.getCandles(intervalMinutes));
    }
}
