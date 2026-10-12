package com.tradecore.risk.web;

import com.tradecore.risk.circuitbreaker.TradingCircuitBreaker;
import com.tradecore.risk.ratelimit.RateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RiskAdminController.class)
@DisplayName("RiskAdminController WebMvc Tests")
class RiskAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TradingCircuitBreaker circuitBreaker;

    @MockBean
    private RateLimiter rateLimiter;

    @Test
    @DisplayName("Should return trading status")
    void shouldReturnStatus() throws Exception {
        when(circuitBreaker.isHalted()).thenReturn(false);
        when(circuitBreaker.getConsecutiveRejectionCount()).thenReturn(0);
        when(rateLimiter.getTrackedKeyCount()).thenReturn(5);

        mockMvc.perform(get("/api/admin/risk/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.halted").value(false))
                .andExpect(jsonPath("$.consecutiveRejections").value(0))
                .andExpect(jsonPath("$.activeRateLimiterKeys").value(5));
    }

    @Test
    @DisplayName("Should trigger manual trading halt")
    void shouldHaltTrading() throws Exception {
        mockMvc.perform(post("/api/admin/risk/halt").param("reason", "Volatility"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.halted").value(true))
                .andExpect(jsonPath("$.reason").value("Volatility"));

        verify(circuitBreaker).haltTrading("Volatility");
    }

    @Test
    @DisplayName("Should trigger resume trading")
    void shouldResumeTrading() throws Exception {
        mockMvc.perform(post("/api/admin/risk/resume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.halted").value(false));

        verify(circuitBreaker).resumeTrading();
    }

    @Test
    @DisplayName("Should reset user rate limit")
    void shouldResetRateLimit() throws Exception {
        mockMvc.perform(post("/api/admin/risk/ratelimit/reset").param("userId", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("alice"));

        verify(rateLimiter).reset("alice");
    }
}
