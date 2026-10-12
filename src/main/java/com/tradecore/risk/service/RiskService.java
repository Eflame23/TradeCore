package com.tradecore.risk.service;

import com.tradecore.risk.model.Order;
import com.tradecore.risk.model.RiskResult;

/**
 * Primary Risk Management Service contract for TradeCore.
 *
 * <p>Enforces pre-trade risk controls before any order reaches the Matching Engine.</p>
 */
public interface RiskService {

    /**
     * Evaluates all pre-trade risk controls against an incoming order:
     * <ul>
     *   <li>Global Trading Halt / Circuit Breaker</li>
     *   <li>Per-User Rate Limiting</li>
     *   <li>Order Field & Precision Validation</li>
     *   <li>Maximum Order Quantity Limits</li>
     *   <li>Position Limit Bounds</li>
     * </ul>
     *
     * @param order the candidate order to validate
     * @return RiskResult indicating acceptance or detailed rejection reason
     */
    RiskResult validate(Order order);
}
