package com.tradecore.risk.web;

import com.tradecore.risk.circuitbreaker.TradingCircuitBreaker;
import com.tradecore.risk.ratelimit.RateLimiter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Administrative endpoints for controlling trading status and inspecting risk/rate-limit state.
 *
 * <p><strong>Security Notice (Local Prototype):</strong></p>
 * <p>In this local development prototype, administrative endpoints are exposed without full OAuth2/RBAC
 * authentication. For production deployment, these endpoints MUST be secured behind administrative
 * credentials, mutual TLS, and role-based access control (RBAC).</p>
 */
@RestController
@RequestMapping("/api/admin/risk")
public class RiskAdminController {

    private final TradingCircuitBreaker circuitBreaker;
    private final RateLimiter rateLimiter;

    public RiskAdminController(TradingCircuitBreaker circuitBreaker, RateLimiter rateLimiter) {
        this.circuitBreaker = circuitBreaker;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Checks the current global trading status.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getTradingStatus() {
        return ResponseEntity.ok(Map.of(
                "halted", circuitBreaker.isHalted(),
                "consecutiveRejections", circuitBreaker.getConsecutiveRejectionCount(),
                "activeRateLimiterKeys", rateLimiter.getTrackedKeyCount()
        ));
    }

    /**
     * Manually halts global trading across all instruments and users.
     */
    @PostMapping("/halt")
    public ResponseEntity<Map<String, Object>> haltTrading(@RequestParam(defaultValue = "Manual admin action") String reason) {
        circuitBreaker.haltTrading(reason);
        return ResponseEntity.ok(Map.of(
                "halted", true,
                "reason", reason,
                "message", "Trading halted successfully"
        ));
    }

    /**
     * Manually resumes global trading after operational verification.
     */
    @PostMapping("/resume")
    public ResponseEntity<Map<String, Object>> resumeTrading() {
        circuitBreaker.resumeTrading();
        return ResponseEntity.ok(Map.of(
                "halted", false,
                "message", "Trading resumed successfully"
        ));
    }

    /**
     * Resets rate limit statistics for a specific user.
     */
    @PostMapping("/ratelimit/reset")
    public ResponseEntity<Map<String, Object>> resetRateLimit(@RequestParam String userId) {
        rateLimiter.reset(userId);
        return ResponseEntity.ok(Map.of(
                "userId", userId,
                "message", "Rate limit reset successfully"
        ));
    }
}
