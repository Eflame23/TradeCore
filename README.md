# TradeCore — High-Performance Trading Exchange Platform

TradeCore is a high-performance prototype trading exchange built with Java 17+, Spring Boot 3, and PostgreSQL, featuring a Zerodha Kite-inspired professional web trading interface.

---

## 🚀 Key Features & Capability Matrix

| Area | Feature | Priority | Implementation Status |
|---|---|---|---|
| **Trading Core** | Limit orders (buy/sell at limit price) | P1 | ✅ Active in `OrderBook` & `MatchingEngine` |
| | Price-time priority matching | P1 | ✅ FIFO TreeMap + Deque ($O(\log P)$) |
| | Partial fills & order cancellation | P1 | ✅ REST API & engine level cancellation |
| | Market orders (buy/sell at best available price) | P2 | ✅ Supported in `OrderBook` & `OrderService` |
| | Self-trade prevention (STP) | P2 | ✅ Automatic cancel-maker on self-match |
| | Multi-asset instruments (Crypto, Stocks, Commodities) | P3 | ✅ `InstrumentMatchingEngineManager` |
| **Money & Safety** | Wallet with cash & asset balances | P1 | ✅ `WalletService` & `/api/wallet` |
| | Atomic settlement & idempotency | P1 | ✅ `@Transactional` & `settlements` table |
| | Double-entry ledger zero-sum invariant check | P1 | ✅ `/api/admin/system/ledger/invariants` |
| | Crash-test / recovery proof | P1 | ✅ `/api/admin/system/crash-test` |
| | Funds reserved when order is placed | P2 | ✅ `reserved_cash` & `reserved_asset` bounds |
| **Risk & Protection** | Input validation (qty > 0, price > 0) | P1 | ✅ `RiskServiceImpl` pre-trade hygiene |
| | Max order size & position limits | P1 | ✅ Per-user thread-safe position locks |
| | Per-user token-bucket rate limiting | P1 | ✅ `TokenBucketRateLimiter` |
| | Circuit breaker / global trading halt | P2 | ✅ `TradingCircuitBreaker` & UI alert |
| | Price-band checks (reject absurd prices) | P2 | ✅ Price band validation in `RiskServiceImpl` |
| **Real-time Experience** | Live Level-2 order book & depth bars | P1 | ✅ `/api/orderbook` & `/api/stream/orderbook` |
| | Live trade feed (SSE / WebSockets) | P1 | ✅ `/api/stream/trades` SSE feed |
| | Candlestick Chart (1m, 5m, 15m + EMA20 & SMA50) | P2 | ✅ HTML5 Canvas charting engine |
| | Market News & AI Sentiment Feed | P2 | ✅ `/api/news` (Bullish / Bearish / Neutral) |
| **Users & Security** | Login / Registration | P2 | ✅ BCrypt password hashing & `/api/auth` |
| | JWT Authentication & Spring Security | P2 | ✅ Stateless JWT Filter & Security Config |
| | Role-based access (User vs Admin) | P2 | ✅ `USER` & `ADMIN` role support |
| **Simulation & Bots** | Market-Maker, Trend & Noise Bots | P1 | ✅ `BotMarketSimulationService` |
| | Start/Stop controls & speed configuration | P1 | ✅ `/api/admin/system/bots/*` |
| | Profit & Loss Leaderboard | P2 | ✅ `/api/leaderboard` ranking users & bots |
| **Transparency & History** | Trade history per user | P1 | ✅ `/api/orders/user/{userId}` |
| | Audit trail with "why this matched" reason | P1 | ✅ `/api/audit/trades` execution proofs |
| | Order history with statuses | P2 | ✅ Persistent `orders` table (OPEN, FILLED, CANCELLED) |
| | Portfolio view with profit/loss | P2 | ✅ Realized/Unrealized P&L & equity calculation |
| **UI/UX** | Zerodha Kite inspired professional dashboard | P1/P2 | ✅ Light & Dark theme toggle, Universal Search |

---

## 🏛️ Architecture & Execution Pipeline

```
Incoming Order Request
       │
       ▼
┌──────────────────┐
│  Security / JWT  │
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│   RiskService    │ ── (Rejected: 400, 429, 422, 503) ──> Client Rejection
└──────┬───────────┘
       │ (Accepted)
       ▼
┌──────────────────┐
│  WalletService   │ ── (Reserves Funds: Cash / Asset)
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│  MatchingEngine  │ ── (Price-Time Priority & Self-Trade Prevention)
└──────┬───────────┘
       │ (Executed Trades)
       ▼
┌──────────────────┐
│  LedgerService   │ ── (Double-Entry Settlement & Unreserve)
└──────┬───────────┘
       │
       ▼
┌──────────────────┐
│   AuditService   │ ── (Records "Why Matched" Proof in trade_audits)
└──────────────────┘
```

---

## 🖥️ Zerodha Kite User Interface (Static Web App)

Located at `http://localhost:8080/` once started:
- **Universal Stock / Crypto / Commodity Search Bar**: Instant search across 15+ assets (`BTC-USD`, `ETH-USD`, `SOL-USD`, `AAPL`, `TSLA`, `NVDA`, `GOLD`, `CRUDEOIL`).
- **Interactive Candlestick Charting**: Custom HTML5 Canvas renderer with Green/Red candles, High/Low wicks, Volume histogram, and **EMA 20 & SMA 50 technical indicator overlays**.
- **Level-2 Market Depth & Order Book**: Top 5/10 Bids and Asks with visual depth bar percentage overlays.
- **Zerodha Order Ticket**: Tabbed Buy/Sell modal supporting LIMIT, MARKET, and STOP-LOSS order types with quick % balance buttons (25%, 50%, 75%, 100%).
- **Multi-Tab Panel**:
  1. *Orders & Positions*: Active/historical orders with status tags and cancel button.
  2. *Portfolio & Allocation*: Real-time Cash, Equity, and P&L (amount & %).
  3. *Match Audit Log*: Detailed execution proofs showing why each order matched.
  4. *Market News*: Live market headlines with AI sentiment indicators.
  5. *Leaderboard*: Trader & Bot net worth rankings.
  6. *Admin Controls*: Global Circuit Breaker halt/resume, Bot simulation, and Crash-Test proof.

---

## ⚡ REST API Endpoints

### Authentication
- `POST /api/auth/register` — Register a new account (`username`, `password`)
- `POST /api/auth/login` — Login and receive JWT access token

### Orders & Matching
- `POST /api/orders` — Submit limit, market, or stop-loss order
- `DELETE /api/orders/{orderId}?userId=alice` — Cancel resting order and release reserved funds
- `GET /api/orders/user/{userId}` — Get order history with status
- `GET /api/orderbook?symbol=BTC-USD` — Get Level-2 order book snapshot

### Markets & Instruments
- `GET /api/instruments` — Get catalog of stocks, crypto, and commodities
- `GET /api/instruments/search?query=AAPL` — Search instruments
- `GET /api/chart/candles?intervalMinutes=1` — Get OHLCV candlestick data
- `GET /api/news` — Get market news & AI sentiment feed

### Wallet, Portfolio & Audit
- `GET /api/wallet/{userId}` — View cash, asset, available, and reserved balances
- `GET /api/portfolio/{userId}` — View total equity, holdings, and P&L
- `GET /api/leaderboard` — View trader profit leaderboard
- `GET /api/audit/trades` — View "why matched" match audit log

### System Administration & Simulation
- `POST /api/admin/risk/halt` — Emergency halt global trading
- `POST /api/admin/risk/resume` — Resume global trading
- `POST /api/admin/system/bots/start` — Start market maker, trend & noise simulation bots
- `POST /api/admin/system/bots/stop` — Stop simulation bots
- `GET /api/admin/system/ledger/invariants` — Execute double-entry zero-sum invariant check
- `POST /api/admin/system/crash-test` — Execute automated crash-test consistency proof

---

## 🛠️ Building & Running

### Prerequisites
- JDK 17 or higher
- Maven (`./mvnw` included)
- PostgreSQL (or in-memory H2 fallback)

### Compile & Run Tests
```bash
./mvnw test
```

### Launch Application
```bash
./mvnw spring-boot:run
```

Open browser at `http://localhost:8080/` to launch the Zerodha Kite Trading Dashboard!
