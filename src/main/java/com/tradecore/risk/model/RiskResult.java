package com.tradecore.risk.model;

/**
 * Result of evaluating risk rules against an order.
 *
 * @param accepted     true if the order passed all risk checks; false otherwise
 * @param rejectReason category of rejection if rejected, or null if accepted
 * @param message      descriptive explanation of the decision
 */
public record RiskResult(
        boolean accepted,
        RiskRejectReason rejectReason,
        String message
) {
    public static RiskResult accept() {
        return new RiskResult(true, null, "Order accepted by risk validation");
    }

    public static RiskResult reject(RiskRejectReason reason, String message) {
        return new RiskResult(false, reason, message);
    }
}
