package com.tradecore.risk.web;

import com.tradecore.risk.model.RiskRejectReason;
import com.tradecore.risk.model.RiskResult;
import com.tradecore.risk.service.RiskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RiskOrderSubmissionExampleController.class)
@DisplayName("RiskOrderSubmissionExampleController WebMvc Tests")
class RiskOrderSubmissionExampleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RiskService riskService;

    @Test
    @DisplayName("Should return 200 OK when order passes risk checks")
    void shouldReturn200WhenAccepted() throws Exception {
        when(riskService.validate(any())).thenReturn(RiskResult.accept());

        String json = """
                {
                    "id": "ord-100",
                    "userId": "alice",
                    "side": "BUY",
                    "price": 50000.00,
                    "qty": 2
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.orderId").value("ord-100"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request on invalid order fields")
    void shouldReturn400OnInvalidFields() throws Exception {
        when(riskService.validate(any()))
                .thenReturn(RiskResult.reject(RiskRejectReason.INVALID_PRICE, "Price must be positive"));

        String json = """
                {
                    "id": "ord-101",
                    "userId": "alice",
                    "side": "BUY",
                    "price": -10.00,
                    "qty": 2
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("INVALID_PRICE"));
    }

    @Test
    @DisplayName("Should return 429 Too Many Requests when rate limit is exceeded")
    void shouldReturn429WhenRateLimitExceeded() throws Exception {
        when(riskService.validate(any()))
                .thenReturn(RiskResult.reject(RiskRejectReason.RATE_LIMIT_EXCEEDED, "Rate limit exceeded"));

        String json = """
                {
                    "id": "ord-102",
                    "userId": "alice",
                    "side": "BUY",
                    "price": 50000.00,
                    "qty": 1
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("Should return 503 Service Unavailable when trading is halted")
    void shouldReturn503WhenTradingHalted() throws Exception {
        when(riskService.validate(any()))
                .thenReturn(RiskResult.reject(RiskRejectReason.TRADING_HALTED, "Trading halted"));

        String json = """
                {
                    "id": "ord-103",
                    "userId": "alice",
                    "side": "BUY",
                    "price": 50000.00,
                    "qty": 1
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("TRADING_HALTED"));
    }

    @Test
    @DisplayName("Should return 422 Unprocessable Entity when position limit is exceeded")
    void shouldReturn422WhenPositionExceeded() throws Exception {
        when(riskService.validate(any()))
                .thenReturn(RiskResult.reject(RiskRejectReason.POSITION_LIMIT_EXCEEDED, "Position limit exceeded"));

        String json = """
                {
                    "id": "ord-104",
                    "userId": "alice",
                    "side": "BUY",
                    "price": 50000.00,
                    "qty": 10
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("POSITION_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("Should return 422 Unprocessable Entity when max quantity is exceeded")
    void shouldReturn422WhenMaxQtyExceeded() throws Exception {
        when(riskService.validate(any()))
                .thenReturn(RiskResult.reject(RiskRejectReason.MAX_QUANTITY_EXCEEDED, "Max qty exceeded"));

        String json = """
                {
                    "id": "ord-105",
                    "userId": "alice",
                    "side": "BUY",
                    "price": 50000.00,
                    "qty": 50
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reason").value("MAX_QUANTITY_EXCEEDED"));
    }
}
