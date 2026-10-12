package com.tradecore.notification.web;

import com.tradecore.audit.AuditService;
import com.tradecore.audit.TradeAudit;
import com.trading.engine.MatchingEngine;
import com.trading.engine.OrderBookMatchingEngine;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/stream")
public class TradeStreamController {

    private final MatchingEngine matchingEngine;
    private final AuditService auditService;

    public TradeStreamController(MatchingEngine matchingEngine, AuditService auditService) {
        this.matchingEngine = matchingEngine;
        this.auditService = auditService;
    }

    @GetMapping(value = "/orderbook", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamOrderBook() {
        SseEmitter emitter = new SseEmitter(180_000L); // 3-minute timeout
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(() -> {
            try {
                if (matchingEngine instanceof OrderBookMatchingEngine engine) {
                    emitter.send(SseEmitter.event()
                            .name("orderbook")
                            .data(engine.snapshot()));
                }
            } catch (Exception e) {
                emitter.complete();
                executor.shutdown();
            }
        }, 0, 500, TimeUnit.MILLISECONDS);

        emitter.onCompletion(executor::shutdown);
        emitter.onTimeout(executor::shutdown);
        return emitter;
    }

    @GetMapping(value = "/trades", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTrades() {
        SseEmitter emitter = new SseEmitter(180_000L);
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(() -> {
            try {
                List<TradeAudit> audits = auditService.getRecentAudits(10);
                emitter.send(SseEmitter.event()
                        .name("trades")
                        .data(audits));
            } catch (Exception e) {
                emitter.complete();
                executor.shutdown();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        emitter.onCompletion(executor::shutdown);
        emitter.onTimeout(executor::shutdown);
        return emitter;
    }
}
