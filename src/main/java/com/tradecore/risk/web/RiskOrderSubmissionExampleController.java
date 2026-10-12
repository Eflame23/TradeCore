package com.tradecore.risk.web;

import com.tradecore.risk.model.Order;
import com.tradecore.risk.model.RiskRejectReason;
import com.tradecore.risk.model.RiskResult;
import com.tradecore.risk.service.RiskService;
import com.tradecore.risk.web.dto.OrderRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Reference Order Submission Controller demonstrating HTTP status mapping for risk validation.
 *
 * <p><strong>HTTP Status Code Mapping Convention:</strong></p>
 * <ul>
 *   <li>Invalid Order Schema / Missing Fields / Non-positive values: <strong>HTTP 400 Bad Request</strong></li>
 *   <li>Rate Limit Exceeded: <strong>HTTP 429 Too Many Requests</strong></li>
 *   <li>Trading Halted / Circuit Breaker Active: <strong>HTTP 503 Service Unavailable</strong></li>
 *   <li>Position or Maximum Order Size Exceeded / Position Unavailable: <strong>HTTP 422 Unprocessable Entity</strong></li>
 *   <li>Accepted Order: <strong>HTTP 200 OK</strong></li>
 * </ul>
 *
 * <p>This controller demonstrates how the unified order endpoint in TradeCore connects
 * RiskService before submitting to the MatchingEngine.</p>
 */
@RestController
@RequestMapping("/api/orders")
public class RiskOrderSubmissionExampleController {

    private final RiskService riskService;

    public RiskOrderSubmissionExampleController(RiskService riskService) {
        this.riskService = riskService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> submitOrder(@RequestBody(required = false) OrderRequest request) {
        if (request == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "status", "REJECTED",
                    "reason", "ORDER_NULL",
                    "message", "Order request body is required"
            ));
        }

        Order order = request.toDomain();
        RiskResult result = riskService.validate(order);

        if (!result.accepted()) {
            HttpStatus status = mapRejectReasonToHttpStatus(result.rejectReason());
            return ResponseEntity.status(status).body(Map.of(
                    "status", "REJECTED",
                    "reason", result.rejectReason() != null ? result.rejectReason().name() : "UNKNOWN",
                    "message", result.message()
            ));
        }

        // If risk accepted, order would proceed to MatchingEngine.submit(order)
        return ResponseEntity.ok(Map.of(
                "status", "ACCEPTED",
                "orderId", order.id(),
                "userId", order.userId(),
                "message", "Order passed pre-trade risk checks"
        ));
    }

    private HttpStatus mapRejectReasonToHttpStatus(RiskRejectReason reason) {
        if (reason == null) {
            return HttpStatus.BAD_REQUEST;
        }

        return switch (reason) {
            case TRADING_HALTED -> HttpStatus.SERVICE_UNAVAILABLE; // 503
            case RATE_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS; // 429
            case MAX_QUANTITY_EXCEEDED, POSITION_LIMIT_EXCEEDED, POSITION_DATA_UNAVAILABLE -> HttpStatus.UNPROCESSABLE_ENTITY; // 422
            case ORDER_NULL, ORDER_ID_INVALID, USER_ID_INVALID, SIDE_INVALID, INVALID_QUANTITY, INVALID_PRICE -> HttpStatus.BAD_REQUEST; // 400
        };
    }
}
