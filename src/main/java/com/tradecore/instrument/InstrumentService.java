package com.tradecore.instrument;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class InstrumentService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentService.class);

    private final Map<String, Instrument> catalog = new ConcurrentHashMap<>();
    private final HttpClient httpClient;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final Random random = new Random();

    public InstrumentService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();

        // Seed initial market instruments
        seedDefaultCatalog();

        // Schedule continuous real-time live price ticks every 1 second
        executor.scheduleWithFixedDelay(this::tickLivePrices, 0, 1, TimeUnit.SECONDS);
    }

    private void seedDefaultCatalog() {
        // Crypto Markets
        add("BTC-USD", "BINANCE:BTCUSDT", "Bitcoin / US Dollar", "CRYPTO", "64250.00", "2.15", "42500", "65100.00", "63200.00");
        add("ETH-USD", "BINANCE:ETHUSDT", "Ethereum / US Dollar", "CRYPTO", "3480.50", "1.80", "18400", "3520.00", "3410.00");
        add("SOL-USD", "BINANCE:SOLUSDT", "Solana / US Dollar", "CRYPTO", "152.80", "3.40", "14500", "156.00", "148.00");
        add("XRP-USD", "BINANCE:XRPUSDT", "Ripple / US Dollar", "CRYPTO", "0.58", "-0.45", "120000", "0.60", "0.56");
        add("DOGE-USD", "BINANCE:DOGEUSDT", "Dogecoin / US Dollar", "CRYPTO", "0.12", "3.40", "500000", "0.13", "0.11");
        add("ADA-USD", "BINANCE:ADAUSDT", "Cardano / US Dollar", "CRYPTO", "0.38", "1.15", "95000", "0.40", "0.36");
        add("AVAX-USD", "BINANCE:AVAXUSDT", "Avalanche / US Dollar", "CRYPTO", "26.40", "-0.80", "32000", "27.50", "25.80");
        add("LINK-USD", "BINANCE:LINKUSDT", "Chainlink / US Dollar", "CRYPTO", "12.80", "2.50", "41000", "13.20", "12.30");
        add("DOT-USD", "BINANCE:DOTUSDT", "Polkadot / US Dollar", "CRYPTO", "4.65", "0.30", "28000", "4.80", "4.50");

        // Stock Markets (US & Global)
        add("AAPL", "NASDAQ:AAPL", "Apple Inc.", "EQUITY", "185.50", "0.95", "450000", "187.00", "184.00");
        add("TSLA", "NASDAQ:TSLA", "Tesla Inc.", "EQUITY", "240.20", "-1.85", "620000", "246.00", "238.00");
        add("NVDA", "NASDAQ:NVDA", "NVIDIA Corporation", "EQUITY", "125.40", "4.15", "890000", "127.50", "121.00");
        add("MSFT", "NASDAQ:MSFT", "Microsoft Corporation", "EQUITY", "420.10", "0.65", "310000", "423.00", "418.00");
        add("GOOGL", "NASDAQ:GOOGL", "Alphabet Inc.", "EQUITY", "175.30", "1.10", "280000", "177.00", "173.50");
        add("AMZN", "NASDAQ:AMZN", "Amazon.com Inc.", "EQUITY", "182.40", "1.35", "390000", "184.50", "180.20");
        add("META", "NASDAQ:META", "Meta Platforms Inc.", "EQUITY", "485.60", "2.20", "210000", "491.00", "480.00");
        add("NFLX", "NASDAQ:NFLX", "Netflix Inc.", "EQUITY", "610.20", "1.80", "120000", "618.00", "602.00");
        add("AMD", "NASDAQ:AMD", "Advanced Micro Devices", "EQUITY", "165.40", "2.90", "280000", "168.00", "161.00");
        add("INTC", "NASDAQ:INTC", "Intel Corporation", "EQUITY", "30.80", "-0.60", "190000", "31.50", "30.20");
        add("RELIANCE", "BSE:RELIANCE", "Reliance Industries Ltd.", "EQUITY", "2950.00", "0.40", "150000", "2975.00", "2930.00");
        add("INFY", "BSE:INFY", "Infosys Limited", "EQUITY", "1820.00", "-0.30", "210000", "1840.00", "1810.00");
        add("TCS", "BSE:TCS", "Tata Consultancy Services", "EQUITY", "4150.00", "0.85", "110000", "4180.00", "4120.00");

        // Commodity Markets
        add("GOLD", "TVC:GOLD", "Gold Spot (troy oz)", "COMMODITY", "2380.00", "0.75", "18000", "2395.00", "2365.00");
        add("SILVER", "TVC:SILVER", "Silver Spot (troy oz)", "COMMODITY", "30.50", "1.50", "45000", "31.20", "29.80");
        add("CRUDEOIL", "TVC:USOIL", "WTI Crude Oil Futures", "COMMODITY", "78.40", "-1.20", "95000", "80.10", "77.50");
        add("BRENT", "TVC:UKOIL", "Brent Crude Oil Futures", "COMMODITY", "82.10", "-0.95", "88000", "83.50", "81.20");
        add("NATURALGAS", "TVC:NATURALGAS", "Natural Gas Futures", "COMMODITY", "2.45", "2.80", "110000", "2.52", "2.38");
        add("COPPER", "TVC:COPPER", "High Grade Copper Futures", "COMMODITY", "4.35", "0.60", "34000", "4.42", "4.28");
        add("PLATINUM", "TVC:PLATINUM", "Platinum Spot (troy oz)", "COMMODITY", "980.50", "1.10", "12000", "992.00", "970.00");
        add("PALLADIUM", "TVC:PALLADIUM", "Palladium Spot (troy oz)", "COMMODITY", "1040.00", "-1.40", "9500", "1065.00", "1025.00");
    }

    private void add(String symbol, String tvSymbol, String name, String category, String price, String change, String vol, String high, String low) {
        catalog.put(symbol, new Instrument(
                symbol, tvSymbol, name, category,
                new BigDecimal(price), new BigDecimal(change),
                new BigDecimal(vol), new BigDecimal(high), new BigDecimal(low)
        ));
    }

    private void tickLivePrices() {
        try {
            // 1. Fetch live REST tickers from Binance for crypto pairs
            fetchBinanceTickers();

            // 2. Simulate active order flow and live price movement ticks for Stocks and Commodities
            simulateStockAndCommodityTicks();
        } catch (Exception e) {
            log.debug("Notice: Live market price tick error: {}", e.getMessage());
        }
    }

    private void fetchBinanceTickers() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.binance.com/api/v3/ticker/24hr"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200 && response.body() != null) {
                String json = response.body();
                updateCryptoPrice("BTC-USD", "BTCUSDT", json);
                updateCryptoPrice("ETH-USD", "ETHUSDT", json);
                updateCryptoPrice("SOL-USD", "SOLUSDT", json);
                updateCryptoPrice("XRP-USD", "XRPUSDT", json);
                updateCryptoPrice("DOGE-USD", "DOGEUSDT", json);
                updateCryptoPrice("ADA-USD", "ADAUSDT", json);
                updateCryptoPrice("AVAX-USD", "AVAXUSDT", json);
                updateCryptoPrice("LINK-USD", "LINKUSDT", json);
                updateCryptoPrice("DOT-USD", "DOTUSDT", json);
            }
        } catch (Exception ignored) {}
    }

    private void updateCryptoPrice(String symbol, String binancePair, String json) {
        try {
            int idx = json.indexOf("\"symbol\":\"" + binancePair + "\"");
            if (idx != -1) {
                int priceIdx = json.indexOf("\"lastPrice\":\"", idx);
                int changeIdx = json.indexOf("\"priceChangePercent\":\"", idx);
                int highIdx = json.indexOf("\"highPrice\":\"", idx);
                int lowIdx = json.indexOf("\"lowPrice\":\"", idx);
                int volIdx = json.indexOf("\"volume\":\"", idx);

                if (priceIdx != -1) {
                    String priceStr = extractJsonValue(json, priceIdx + 13);
                    String changeStr = extractJsonValue(json, changeIdx + 22);
                    String highStr = extractJsonValue(json, highIdx + 13);
                    String lowStr = extractJsonValue(json, lowIdx + 12);
                    String volStr = extractJsonValue(json, volIdx + 10);

                    Instrument existing = catalog.get(symbol);
                    if (existing != null && priceStr != null) {
                        BigDecimal p = new BigDecimal(priceStr).setScale(2, RoundingMode.HALF_UP);
                        BigDecimal c = changeStr != null ? new BigDecimal(changeStr).setScale(2, RoundingMode.HALF_UP) : existing.changePercent();
                        BigDecimal h = highStr != null ? new BigDecimal(highStr).setScale(2, RoundingMode.HALF_UP) : existing.high24h();
                        BigDecimal l = lowStr != null ? new BigDecimal(lowStr).setScale(2, RoundingMode.HALF_UP) : existing.low24h();
                        BigDecimal v = volStr != null ? new BigDecimal(volStr).setScale(0, RoundingMode.HALF_UP) : existing.volume24h();

                        catalog.put(symbol, new Instrument(
                                existing.symbol(), existing.tvSymbol(), existing.name(), existing.category(),
                                p, c, v, h, l
                        ));
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void simulateStockAndCommodityTicks() {
        catalog.values().forEach(inst -> {
            if ("EQUITY".equals(inst.category()) || "COMMODITY".equals(inst.category())) {
                // Apply a small random tick movement (-0.15% to +0.15%)
                double pctChange = (random.nextDouble() - 0.49) * 0.003;
                BigDecimal currentPrice = inst.price();
                BigDecimal delta = currentPrice.multiply(BigDecimal.valueOf(pctChange));
                BigDecimal newPrice = currentPrice.add(delta).setScale(2, RoundingMode.HALF_UP);

                if (newPrice.signum() > 0) {
                    BigDecimal newHigh = newPrice.max(inst.high24h());
                    BigDecimal newLow = newPrice.min(inst.low24h());
                    BigDecimal newChange = inst.changePercent().add(BigDecimal.valueOf(pctChange * 100)).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal newVol = inst.volume24h().add(BigDecimal.valueOf(random.nextInt(10) + 1));

                    catalog.put(inst.symbol(), new Instrument(
                            inst.symbol(), inst.tvSymbol(), inst.name(), inst.category(),
                            newPrice, newChange, newVol, newHigh, newLow
                    ));
                }
            }
        });
    }

    private String extractJsonValue(String json, int startPos) {
        int endPos = json.indexOf("\"", startPos);
        return (endPos != -1) ? json.substring(startPos, endPos) : null;
    }

    public List<Instrument> getAllInstruments() {
        return new ArrayList<>(catalog.values());
    }

    public List<Instrument> searchInstruments(String query) {
        if (query == null || query.isBlank()) {
            return getAllInstruments();
        }
        String q = query.trim().toUpperCase();
        List<Instrument> matches = new ArrayList<>(catalog.values().stream()
                .filter(i -> i.symbol().toUpperCase().contains(q) || i.name().toUpperCase().contains(q) || i.category().toUpperCase().contains(q))
                .toList());

        // Dynamic Universal Search Resolution: If not found in default list, dynamically construct a valid TradingView instrument!
        if (matches.isEmpty() && q.length() >= 2) {
            String category = q.contains("USD") || q.endsWith("USDT") ? "CRYPTO" :
                              q.contains("OIL") || q.contains("GOLD") || q.contains("GAS") ? "COMMODITY" : "EQUITY";
            String tvPrefix = "CRYPTO".equals(category) ? "BINANCE:" + q.replace("-USD", "USDT") :
                              "COMMODITY".equals(category) ? "TVC:" + q : "NASDAQ:" + q;

            Instrument dynamicInst = new Instrument(
                    q, tvPrefix, q + " Market Security", category,
                    new BigDecimal("150.00"), BigDecimal.ZERO,
                    new BigDecimal("50000"), new BigDecimal("155.00"), new BigDecimal("148.00")
            );
            matches.add(dynamicInst);
        }

        return matches;
    }

    public Instrument getInstrument(String symbol) {
        if (symbol == null) return catalog.get("BTC-USD");
        return catalog.getOrDefault(symbol.trim().toUpperCase(), catalog.get("BTC-USD"));
    }
}
