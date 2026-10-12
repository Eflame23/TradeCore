package com.tradecore.risk.integration;

import com.tradecore.ledger.service.LedgerService;
import com.tradecore.risk.model.Order;
import com.tradecore.risk.model.RiskRejectReason;
import com.tradecore.risk.model.RiskResult;
import com.tradecore.risk.model.Side;
import com.tradecore.risk.service.RiskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pipeline Integration & Matching Engine Invariant Tests")
class PipelineIntegrationDemoTest {

    @Mock
    private RiskService riskService;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private PipelineIntegrationDemo.MatchingEngineSimulator matchingEngineSimulator;

    private PipelineIntegrationDemo pipelineDemo;

    @BeforeEach
    void setUp() {
        pipelineDemo = new PipelineIntegrationDemo(riskService, ledgerService);
    }

    @Test
    @DisplayName("16. Rejected orders NEVER reach the matching engine")
    void rejectedOrdersNeverReachMatchingEngine() {
        Order invalidOrder = new Order("ord-bad", "alice", Side.BUY, new BigDecimal("100"), 50L, Instant.now());
        when(riskService.validate(invalidOrder))
                .thenReturn(RiskResult.reject(RiskRejectReason.MAX_QUANTITY_EXCEEDED, "Exceeded limit"));

        boolean processed = pipelineDemo.processOrder(invalidOrder, matchingEngineSimulator);

        assertFalse(processed, "Pipeline should return false for risk-rejected order");
        // Verify matching engine submit was never called!
        verify(matchingEngineSimulator, never()).submit(any());
    }

    @Test
    @DisplayName("Accepted orders proceed to the matching engine")
    void acceptedOrdersReachMatchingEngine() {
        Order validOrder = new Order("ord-good", "alice", Side.BUY, new BigDecimal("100"), 2L, Instant.now());
        when(riskService.validate(validOrder)).thenReturn(RiskResult.accept());

        boolean processed = pipelineDemo.processOrder(validOrder, matchingEngineSimulator);

        assertTrue(processed);
        verify(matchingEngineSimulator, times(1)).submit(validOrder);
    }
}
