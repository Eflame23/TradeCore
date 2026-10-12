package com.tradecore.instrument.web;

import com.tradecore.instrument.Instrument;
import com.tradecore.instrument.InstrumentService;
import com.tradecore.news.MarketNews;
import com.tradecore.news.MarketNewsService;
import com.trading.engine.InstrumentMatchingEngineManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class InstrumentController {

    private final InstrumentService instrumentService;
    private final MarketNewsService newsService;
    private final InstrumentMatchingEngineManager matchingEngineManager;

    public InstrumentController(InstrumentService instrumentService,
                                MarketNewsService newsService,
                                InstrumentMatchingEngineManager matchingEngineManager) {
        this.instrumentService = instrumentService;
        this.newsService = newsService;
        this.matchingEngineManager = matchingEngineManager;
    }

    @GetMapping("/instruments")
    public ResponseEntity<List<Instrument>> getInstruments() {
        return ResponseEntity.ok(instrumentService.getAllInstruments());
    }

    @GetMapping("/instruments/search")
    public ResponseEntity<List<Instrument>> searchInstruments(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(instrumentService.searchInstruments(query));
    }

    @GetMapping("/news")
    public ResponseEntity<List<MarketNews>> getMarketNews(@RequestParam(required = false) String symbol) {
        if (symbol != null && !symbol.isBlank()) {
            return ResponseEntity.ok(newsService.getNewsForSymbol(symbol));
        }
        return ResponseEntity.ok(newsService.getLatestNews());
    }
}
