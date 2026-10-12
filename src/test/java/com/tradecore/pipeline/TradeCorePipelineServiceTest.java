package com.tradecore.pipeline;

import com.tradecore.ledger.service.LedgerService;
import com.tradecore.risk.model.Order;
import com.tradecore.risk.model.RiskRejectReason;
import com.tradecore.risk.model.RiskResult;
import com.tradecore.risk.model.Side;
import com.tradecore.risk.service.RiskService;
import com.trading.engine.MatchingEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TradeCore Pipeline Integration Service Tests")
class TradeCorePipelineServiceTest {

    @Mock
    private RiskService riskService;

    @Mock
    private MatchingEngine matchingEngine;

    @Mock
    private LedgerService ledgerService;

    private TradeCorePipelineService pipelineService;

    @BeforeEach
    void setUp() {
        pipelineService = new TradeCorePipelineService(riskService, matchingEngine, ledgerService);
    }

    @Test
    @DisplayName("Risk rejection should stop processing and not call matching engine or ledger")
    void riskRejectionShouldStopPipeline() {
        Order invalidOrder = new Order("ord-1", "alice", Side.BUY, new BigDecimal("100"), 50L, Instant.now());
        when(riskService.validate(invalidOrder))
                .thenReturn(RiskResult.reject(RiskRejectReason.MAX_QUANTITY_EXCEEDED, "Qty too large"));

        TradeCorePipelineService.PipelineResult result = pipelineService.processOrder(invalidOrder);

        assertFalse(result.accepted());
        assertEquals(RiskRejectReason.MAX_QUANTITY_EXCEEDED, result.riskResult().rejectReason());
        verify(matchingEngine, never()).submit(any());
        verify(ledgerService, never()).settle(any());
    }

    @Test
    @DisplayName("Accepted order should match and settle trades in ledger")
    void acceptedOrderShouldMatchAndSettle() {
        Order validOrder = new Order("ord-buy", "alice", Side.BUY, new BigDecimal("50000"), 2L, Instant.now());
        when(riskService.validate(validOrder)).thenReturn(RiskResult.accept());

        com.trading.common.Trade executedTrade = new com.trading.common.Trade(
                "trade-1", "ord-buy", "ord-sell", "alice", "bob", new BigDecimal("50000"), 2L, Instant.now()
        );
        when(matchingEngine.submit(any())).thenReturn(List.of(executedTrade));
        when(ledgerService.settle(any())).thenReturn(true);

        TradeCorePipelineService.PipelineResult result = pipelineService.processOrder(validOrder);

        assertTrue(result.accepted());
        assertEquals(1, result.executedTradeCount());
        assertEquals(1, result.settledTrades().size());
        assertEquals("trade-1", result.settledTrades().get(0).tradeId());

        verify(matchingEngine, times(1)).submit(any());
        verify(ledgerService, times(1)).settle(any());
    }
}
