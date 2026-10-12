package com.tradecore.admin.web;

import com.tradecore.bot.BotMarketSimulationService;
import com.tradecore.risk.circuitbreaker.TradingCircuitBreaker;
import com.tradecore.risk.ratelimit.RateLimiter;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/system")
public class AdminController {

    private final TradingCircuitBreaker circuitBreaker;
    private final RateLimiter rateLimiter;
    private final BotMarketSimulationService botService;
    private final JdbcTemplate jdbcTemplate;

    public AdminController(TradingCircuitBreaker circuitBreaker,
                           RateLimiter rateLimiter,
                           BotMarketSimulationService botService,
                           JdbcTemplate jdbcTemplate) {
        this.circuitBreaker = circuitBreaker;
        this.rateLimiter = rateLimiter;
        this.botService = botService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping("/bots/start")
    public ResponseEntity<Map<String, Object>> startBots() {
        botService.startBots();
        return ResponseEntity.ok(Map.of("message", "Market simulation bots started", "running", botService.isRunning()));
    }

    @PostMapping("/bots/stop")
    public ResponseEntity<Map<String, Object>> stopBots() {
        botService.stopBots();
        return ResponseEntity.ok(Map.of("message", "Market simulation bots stopped", "running", botService.isRunning()));
    }

    @GetMapping("/bots/status")
    public ResponseEntity<Map<String, Object>> getBotStatus() {
        return ResponseEntity.ok(Map.of(
                "running", botService.isRunning(),
                "intervalMs", botService.getIntervalMs()
        ));
    }

    @PostMapping("/bots/config")
    public ResponseEntity<Map<String, Object>> configBots(@RequestParam long intervalMs) {
        botService.setIntervalMs(intervalMs);
        return ResponseEntity.ok(Map.of("message", "Bot interval updated", "intervalMs", botService.getIntervalMs()));
    }

    @GetMapping("/ledger/invariants")
    public ResponseEntity<Map<String, Object>> checkLedgerInvariants() {
        List<Map<String, Object>> nonZeroSums = jdbcTemplate.queryForList(
                "SELECT currency, SUM(amount) AS net_sum FROM ledger_entries GROUP BY currency HAVING SUM(amount) <> 0"
        );

        boolean invariantHolds = nonZeroSums.isEmpty();
        return ResponseEntity.ok(Map.of(
                "invariantHolds", invariantHolds,
                "status", invariantHolds ? "PASSED: Zero-Sum Balanced" : "FAILED: Balance Mismatch Detected",
                "discrepancies", nonZeroSums
        ));
    }

    @PostMapping("/crash-test")
    public ResponseEntity<Map<String, Object>> runCrashTest() {
        List<Map<String, Object>> nonZeroSums = jdbcTemplate.queryForList(
                "SELECT currency, SUM(amount) AS net_sum FROM ledger_entries GROUP BY currency HAVING SUM(amount) <> 0"
        );

        Integer totalSettlements = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM settlements", Integer.class);
        Integer totalAudits = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM trade_audits", Integer.class);
        Integer totalLedgerEntries = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ledger_entries", Integer.class);

        boolean healthy = nonZeroSums.isEmpty();

        Map<String, Object> report = new HashMap<>();
        report.put("testStatus", healthy ? "CRASH_PROOF_PASSED" : "INTEGRITY_VIOLATION");
        report.put("doubleEntryZeroSumHolds", healthy);
        report.put("totalSettledTrades", totalSettlements != null ? totalSettlements : 0);
        report.put("totalTradeAudits", totalAudits != null ? totalAudits : 0);
        report.put("totalLedgerEntries", totalLedgerEntries != null ? totalLedgerEntries : 0);
        report.put("explanation", healthy
                ? "Crash-test verification passed! Double-entry zero-sum invariant holds across all settled trades."
                : "Discrepancy detected in ledger zero-sum check.");

        return ResponseEntity.ok(report);
    }
}
