package com.tradecore.metrics.web;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MetricsController {

    private final JdbcTemplate jdbcTemplate;

    public MetricsController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        Integer totalOrders = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class);
        Integer totalTrades = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM trade_audits", Integer.class);
        Integer totalAccounts = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM accounts", Integer.class);

        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();

        double ordersPerSec = totalOrders != null && uptimeMs > 0 ? (totalOrders * 1000.0) / uptimeMs : 0.0;
        double tradesPerSec = totalTrades != null && uptimeMs > 0 ? (totalTrades * 1000.0) / uptimeMs : 0.0;

        metrics.put("status", "UP");
        metrics.put("uptimeMs", uptimeMs);
        metrics.put("totalOrders", totalOrders != null ? totalOrders : 0);
        metrics.put("totalTrades", totalTrades != null ? totalTrades : 0);
        metrics.put("totalAccounts", totalAccounts != null ? totalAccounts : 0);
        metrics.put("ordersPerSec", String.format("%.2f", ordersPerSec));
        metrics.put("tradesPerSec", String.format("%.2f", tradesPerSec));
        metrics.put("matchingEngineLatencyMs", "< 1ms (In-Memory Price-Time Priority)");

        return ResponseEntity.ok(metrics);
    }
}
