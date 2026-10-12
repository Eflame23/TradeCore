package com.tradecore.risk.integration;

import com.tradecore.ledger.service.LedgerService;
import com.tradecore.risk.model.Order;
import com.tradecore.risk.model.RiskResult;
import com.tradecore.risk.service.RiskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Demonstrates the canonical TradeCore end-to-end integration flow:
 *
 * <pre>
 *   RiskService.validate(order)
 *       -> [MatchingEngine.submit(order)]
 *           -> LedgerService.settle(trade)
 *               -> EventPublisher.publish(trade)
 * </pre>
 *
 * <p><strong>Key Invariant:</strong>
 * Rejected orders immediately abort processing and NEVER reach the Matching Engine.</p>
 */
@Component
public class PipelineIntegrationDemo {

    private static final Logger log = LoggerFactory.getLogger(PipelineIntegrationDemo.class);

    private final RiskService riskService;
    private final LedgerService ledgerService;

    public PipelineIntegrationDemo(RiskService riskService, LedgerService ledgerService) {
        this.riskService = riskService;
        this.ledgerService = ledgerService;
    }

    /**
     * Executes the pipeline flow for an order.
     *
     * @param order the order to submit
     * @param matchingEngineSimulator simulated matching engine function
     * @return boolean true if order was accepted and reached matching engine; false if rejected by risk
     */
    public boolean processOrder(Order order, MatchingEngineSimulator matchingEngineSimulator) {
        // Step 1: Pre-trade risk validation
        RiskResult riskResult = riskService.validate(order);
        if (!riskResult.accepted()) {
            log.warn("Order {} rejected by risk management (reason: {}). Matching engine will NOT be invoked.",
                    order != null ? order.id() : "null", riskResult.rejectReason());
            return false;
        }

        // Step 2: Order reached Matching Engine
        log.info("Order {} accepted by risk management. Forwarding to matching engine.", order.id());
        matchingEngineSimulator.submit(order);
        return true;
    }

    @FunctionalInterface
    public interface MatchingEngineSimulator {
        void submit(Order order);
    }
}
