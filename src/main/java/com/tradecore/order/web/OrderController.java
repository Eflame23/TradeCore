package com.tradecore.order.web;

import com.tradecore.order.model.OrderEntity;
import com.tradecore.order.service.OrderService;
import com.tradecore.pipeline.TradeCorePipelineService.PipelineResult;
import com.tradecore.risk.model.Order;
import com.tradecore.risk.model.RiskRejectReason;
import com.tradecore.risk.web.dto.OrderRequest;
import com.trading.engine.MatchingEngine;
import com.trading.engine.OrderBookMatchingEngine;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class OrderController {

    private final OrderService orderService;
    private final MatchingEngine matchingEngine;

    public OrderController(OrderService orderService, MatchingEngine matchingEngine) {
        this.orderService = orderService;
        this.matchingEngine = matchingEngine;
    }

    @PostMapping("/orders")
    public ResponseEntity<?> submitOrder(@RequestBody OrderRequest request) {
        Order domainOrder = request.toDomain();
        PipelineResult result = orderService.processOrder(domainOrder);

        if (!result.accepted()) {
            RiskRejectReason reason = result.riskResult().rejectReason();
            HttpStatus status = switch (reason) {
                case ORDER_NULL, ORDER_ID_INVALID, USER_ID_INVALID, SIDE_INVALID, INVALID_QUANTITY, INVALID_PRICE, PRICE_OUT_OF_BAND -> HttpStatus.BAD_REQUEST;
                case RATE_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
                case TRADING_HALTED -> HttpStatus.SERVICE_UNAVAILABLE;
                case MAX_QUANTITY_EXCEEDED, POSITION_LIMIT_EXCEEDED, POSITION_DATA_UNAVAILABLE, INSUFFICIENT_FUNDS -> HttpStatus.UNPROCESSABLE_ENTITY;
            };

            return ResponseEntity.status(status).body(new ErrorResponse(reason.name(), result.riskResult().message()));
        }

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/orders/{orderId}")
    public ResponseEntity<?> cancelOrder(@PathVariable String orderId, @RequestParam(defaultValue = "alice") String userId) {
        boolean cancelled = orderService.cancelOrder(orderId, userId);
        if (cancelled) {
            return ResponseEntity.ok(new MessageResponse("Order " + orderId + " cancelled successfully"));
        } else {
            return ResponseEntity.badRequest().body(new ErrorResponse("CANCEL_FAILED", "Order not found or cannot be cancelled"));
        }
    }

    @GetMapping("/orders/user/{userId}")
    public ResponseEntity<List<OrderEntity>> getUserOrders(@PathVariable String userId) {
        return ResponseEntity.ok(orderService.getUserOrders(userId));
    }

    @GetMapping("/orderbook")
    public ResponseEntity<?> getOrderBook(@RequestParam(defaultValue = "BTC-USD") String symbol) {
        if (matchingEngine instanceof com.trading.engine.InstrumentMatchingEngineManager manager) {
            return ResponseEntity.ok(manager.snapshot(symbol));
        } else if (matchingEngine instanceof OrderBookMatchingEngine engine) {
            return ResponseEntity.ok(engine.snapshot());
        }
        return ResponseEntity.ok(new MessageResponse("OrderBook engine not available"));
    }

    public record ErrorResponse(String error, String message) {}
    public record MessageResponse(String message) {}
}
