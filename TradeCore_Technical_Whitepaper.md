# TradeCore Exchange Engine: Technical Architecture & System Implementation Whitepaper

**Author:** Senior Systems & Software Architecture Engineering  
**Platform:** TradeCore High-Performance Trading Exchange  
**Target Stack:** Java 17, Spring Boot 3, Spring Security (JWT), Spring JDBC, PostgreSQL, Server-Sent Events (SSE)  
**Version:** 1.0.0-PROTOTYPE  

---

## Abstract

TradeCore is an institutional-grade prototype electronic trading exchange engine designed to achieve deterministic price-time priority matching, strict pre-trade risk management, double-entry ledger settlement, and low-latency real-time market data streaming. The architecture prioritizes **financial invariants**: zero-sum balance balance accounting, double-entry financial auditability, deadlock-free pessimistic locking, and self-trade prevention (STP).

This document serves as the formal technical whitepaper detailing the end-to-end architecture, domain models, mathematical algorithms, concurrency design patterns, persistence schemas, and administrative safety controls governing TradeCore.

---

## 1. Architectural Principles & System Invariants

The TradeCore architecture is governed by five mandatory system invariants:

1. **Deterministic Price-Time Priority (FIFO)**: Limit orders at superior price levels execute first. At identical price levels, orders execute strictly in arrival timestamp order.
2. **Atomic Capital Reservation**: Capital required for buy orders (cash) or sell orders (assets) is immediately locked upon order submission. No order enters the matching engine without validated funding.
3. **Double-Entry Financial Zero-Sum Accounting**: Every executed trade produces exactly four balanced ledger entries across two participants. Across the platform, $\sum \text{ledger\_entry.amount} = 0$ for every asset class.
4. **Deadlock-Free Resource Locking**: Account row lock acquisition during settlement follows a total lexicographical ordering over participant user IDs.
5. **Self-Trade Prevention (STP)**: Taker orders matching against resting orders from the same user account trigger automatic cancellation of the resting maker order, preventing artificial volume inflation and wash trading.

---

## 2. End-to-End Request Pipeline Architecture

Every order submitted to TradeCore transitions through a strictly structured multi-stage pipeline:

```
                            ┌────────────────────────────────────────┐
                            │           HTTP REST Request            │
                            │           (POST /api/orders)           │
                            └───────────────────┬────────────────────┘
                                                │
                                                ▼
                            ┌────────────────────────────────────────┐
                            │      Stateless JWT Security Filter     │
                            │           (JwtTokenProvider)           │
                            └───────────────────┬────────────────────┘
                                                │
                                                ▼
                            ┌────────────────────────────────────────┐
                            │     Stage 1: Pre-Trade Risk Engine     │
                            │           (RiskServiceImpl)            │
                            └───────┬────────────────────────┬───────┘
                           Rejected │                        │ Accepted
                                    ▼                        ▼
                        ┌───────────────────────┐ ┌────────────────────────────────────────┐
                        │ Persistent DB Record: │ │     Stage 2: Wallet Capital Lock       │
                        │ status = 'REJECTED'   │ │            (WalletService)             │
                        └───────────────────────┘ └───────┬────────────────────────┬───────┘
                                                 Insufficient │                    │ Reserved
                                                              ▼                    ▼
                                                  ┌───────────────────┐ ┌────────────────────┐
                                                  │ Reject Order      │ │ Persistent Record: │
                                                  │ (INSUFFICIENT_    │ │ status = 'OPEN'    │
                                                  │  FUNDS)           │ └─────────┬──────────┘
                                                  └───────────────────┘           │
                                                                                  ▼
                                                                ┌───────────────────────────────────┐
                                                                │  Stage 3: In-Memory Engine Match  │
                                                                │ (InstrumentMatchingEngineManager) │
                                                                └─────────────────┬─────────────────┘
                                                                                  │ Executed Trades
                                                                                  ▼
                                                                ┌───────────────────────────────────┐
                                                                │   Stage 4: Atomic Settlement      │
                                                                │ (LedgerServiceImpl - Double Entry)│
                                                                └─────────────────┬─────────────────┘
                                                                                  │
                                                                                  ▼
                                                                ┌───────────────────────────────────┐
                                                                │    Stage 5: Audit & Broadcast     │
                                                                │ (AuditService & SSE Trade Stream) │
                                                                └───────────────────────────────────┘
```

---

## 3. Subsystem Technical Implementation Specifications

### 3.1 High-Performance Matching Engine (`com.trading.engine`)

The core execution engine provides deterministic order matching across multiple instruments without relying on heavyweight external frameworks.

#### 3.1.1 OrderBook Implementation (`OrderBook.java`)
The order book maintains separate side structures using sorted red-black tree maps (`java.util.TreeMap`) combined with double-ended queues (`java.util.ArrayDeque`):

* **Bids Structure**: `TreeMap<BigDecimal, Deque<Resting>> bids` instantiated with `Comparator.reverseOrder()`. The highest buy price is retrieved in $O(\log P)$ time via `bids.firstKey()`.
* **Asks Structure**: `TreeMap<BigDecimal, Deque<Resting>> asks` instantiated with natural order. The lowest sell price is retrieved in $O(\log P)$ time via `asks.firstKey()`.
* **Time Priority Queue**: Each price node points to an `ArrayDeque<Resting>` containing resting orders at that exact price level. Adding or polling from the queue is an $O(1)$ operation.

```
       BIDS (High -> Low)                         ASKS (Low -> High)
 Price Level    Resting Queue               Price Level    Resting Queue
┌────────────┐ ┌────────────────────────┐  ┌────────────┐ ┌────────────────────────┐
│ $50,250.00 │ │ [Ord#1, Qty:2] -> #2   │  │ $50,260.00 │ │ [Ord#5, Qty:1] -> #6   │
├────────────┤ └────────────────────────┘  ├────────────┤ └────────────────────────┘
│ $50,240.00 │ │ [Ord#3, Qty:5]          │  │ $50,270.00 │ │ [Ord#7, Qty:10]        │
└────────────┘ └────────────────────────┘  └────────────┘ └────────────────────────┘
```

#### 3.1.2 Matching Algorithm Mechanics
1. **Price Crossing Check**:
   * For **Limit Buy**: Crosses if `incoming.price >= bestAsk`.
   * For **Limit Sell**: Crosses if `incoming.price <= bestBid`.
   * For **Market Orders**: Ignores price checks and sweeps available liquidity until quantity is exhausted or the book empties.
2. **Execution Price Determination**: Executions occur strictly at the **resting order's price** (maker price), rewarding liquidity provision.
3. **Self-Trade Prevention (STP)**:
   ```java
   if (maker.order.userId().equalsIgnoreCase(taker.order.userId())) {
       level.pollFirst(); // Cancel resting maker order to prevent self-trade
       continue;
   }
   ```
4. **Resting Unfilled Remainder**: If `taker.remaining > 0` and the order is a `LIMIT` order with `price > 0`, the remainder is pushed to the tail of the corresponding queue (`addLast`).

#### 3.1.3 Concurrency & Multi-Asset Management
* **Single Engine Locking**: [OrderBookMatchingEngine](class://com.trading.engine.OrderBookMatchingEngine) encapsulates matching logic inside a fair `java.util.concurrent.locks.ReentrantLock(true)`. Fairness guarantees FIFO order processing for concurrent thread submissions.
* **Multi-Instrument Isolation**: [InstrumentMatchingEngineManager](class://com.trading.engine.InstrumentMatchingEngineManager) routes requests via `ConcurrentHashMap<String, OrderBookMatchingEngine>`. Each symbol (`BTC-USD`, `ETH-USD`, `AAPL`, etc.) has its own independent lock, preventing cross-symbol contention.

---

### 3.2 Pre-Trade Risk Management & Safety Controls (`com.tradecore.risk`)

The risk layer validates orders before funds are reserved or engine matching occurs.

#### 3.2.1 Pipeline Validation Sequence (`RiskServiceImpl.java`)
1. **Circuit Breaker Status Check**: Verifies global exchange availability.
2. **Order Integrity Rules**: Null checks, positive quantity (`qty > 0`), and positive price validation for limit orders.
3. **Token Bucket Rate Limiting**: Ensures single users do not exceed maximum submission velocity.
4. **Maximum Single-Order Limit**: Rejects orders where `qty > maxOrderQuantity`.
5. **Position Exposure Limit Check**: Rejects orders that would cause projected account positions to exceed maximum absolute thresholds (`maxAbsolutePosition`).

#### 3.2.2 Emergency Circuit Breaker (`TradingCircuitBreaker.java`)
* **State Control**: Thread-safe global halt state managed via `AtomicBoolean halted`.
* **Auto-Tripping**: Tracks consecutive risk rejections via `AtomicInteger consecutiveRejections`. If threshold is breached, `halted.compareAndSet(false, true)` trips the circuit breaker automatically.
* **Safety Invariant**: Once tripped, trading **never** resumes automatically. Manual administrative intervention (`/api/admin/risk/resume`) is strictly required.

#### 3.2.3 Token Bucket Rate Limiter (`TokenBucketRateLimiter.java`)
* **Algorithm**: Smooth token replenishment formula:
  $$\text{tokensToAdd} = (t_{\text{now}} - t_{\text{last\_refill}}) \times \left( \frac{\text{capacity}}{\text{windowSeconds} \times 1000} \right)$$
* **Fine-Grained Locking**: Each user key maps to an isolated `Bucket` with its own `ReentrantLock`.
* **Memory Protection**: Includes automated eviction (`evictInactiveEntries()`) removing user buckets idle past `rateLimiterInactivityMinutes`.

---

### 3.3 Capital Reservation & Atomic Double-Entry Settlement (`com.tradecore.ledger`)

#### 3.3.1 Wallet Capital Locking (`WalletService.java`)
Before matching, capital is reserved to ensure settlement solvency:
* **BUY Orders**: `reserved_cash = reserved_cash + (price * qty)`.
* **SELL Orders**: `reserved_asset = reserved_asset + qty`.
* **Available Capital**:
  $$\text{availableCash} = \text{cashBalance} - \text{reservedCash}$$
  $$\text{availableAsset} = \text{assetBalance} - \text{reservedAsset}$$

#### 3.3.2 Atomic Settlement & Deadlock Prevention (`LedgerServiceImpl.java`)
Trade settlement executes inside `@Transactional(isolation = Isolation.READ_COMMITTED)`:

1. **Idempotent Claim**:
   ```sql
   INSERT INTO settlements (trade_id, settled_at)
   SELECT ?, CURRENT_TIMESTAMP
   WHERE NOT EXISTS (SELECT 1 FROM settlements WHERE trade_id = ?);
   ```
   If row count returned is `0`, settlement returns `false` immediately without side effects.

2. **Deadlock Avoidance Locking Protocol**: To prevent database deadlocks when two trades involving the same users execute concurrently in opposite directions (e.g., Alice vs Bob and Bob vs Alice), accounts are ordered lexicographically before locking:
   ```java
   String firstUser = buyerId.compareTo(sellerId) < 0 ? buyerId : sellerId;
   String secondUser = buyerId.compareTo(sellerId) < 0 ? sellerId : buyerId;

   Account firstAccount = lockAccount(firstUser);   // SELECT ... FOR UPDATE
   Account secondAccount = lockAccount(secondUser); // SELECT ... FOR UPDATE
   ```

3. **Double-Entry Ledger Balancing**:
   Four entries are written to `ledger_entries` in a single batch insert:

   | User | Currency | Amount | Purpose |
   |---|---|---|---|
   | Buyer | USD | $-(P \times Q)$ | Debit Cash |
   | Seller | USD | $+(P \times Q)$ | Credit Cash |
   | Seller | Asset | $-Q$ | Debit Asset |
   | Buyer | Asset | $+Q$ | Credit Asset |

4. **Zero-Sum Verification**:
   The ledger enforces that:
   $$\sum \text{amount}_{\text{USD}} = 0 \quad \text{and} \quad \sum \text{amount}_{\text{BTC}} = 0$$

---

### 3.4 Audit Trail, Real-Time Streaming & Event Architecture (`com.tradecore.audit`, `com.tradecore.notification`)

* **Trade Audit Log**: [AuditService](class://com.tradecore.audit.AuditService) records complete execution proofs into `trade_audits`, including maker/taker order IDs, execution prices, quantities, and human-readable price-time priority reasons.
* **Spring Event Publishing**: Upon successful settlement, `LedgerServiceImpl` fires a `TradeSettledEvent`.
* **Server-Sent Events (SSE) Streaming**: [TradeStreamController](class://com.tradecore.notification.web.TradeStreamController) exposes asynchronous SSE emitters:
  * `/api/stream/orderbook`: Pushes snapshot updates at 500ms intervals.
  * `/api/stream/trades`: Pushes trade audit records at 1000ms intervals.

---

### 3.5 Authentication & Access Control (`com.tradecore.auth`)

* **Stateless JWT Security**: [JwtTokenProvider](class://com.tradecore.auth.JwtTokenProvider) issues HMAC-SHA256 tokens valid for 24 hours containing `userId`, `username`, and `role`.
* **Spring Security Config**: [SecurityConfig](class://com.tradecore.auth.SecurityConfig) establishes stateless session policy (`SessionCreationPolicy.STATELESS`) with disabled CSRF for API endpoints.

---

### 3.6 Automated Market Simulation Engine (`com.tradecore.bot`)

[BotMarketSimulationService](class://com.tradecore.bot.BotMarketSimulationService) uses a `ScheduledExecutorService` to simulate realistic exchange liquidity:
1. **Market Maker Bot (`bot_mm`)**: Submits continuous limit buys/sells around the current reference price with configurable spreads.
2. **Trend Follower Bot (`bot_trend`)**: Models directional price momentum.
3. **Noise Bot (`bot_noise`)**: Simulates random retail market and limit orders.

---

## 4. Relational Database Schema Specification

The database layer relies on ANSI SQL standards compatible with PostgreSQL and H2.

### 4.1 Table Definitions

```sql
-- Accounts & Capital Reservation
CREATE TABLE accounts (
    user_id VARCHAR(64) PRIMARY KEY,
    cash_balance NUMERIC(20, 8) NOT NULL CHECK (cash_balance >= 0),
    asset_balance NUMERIC(20, 8) NOT NULL CHECK (asset_balance >= 0),
    reserved_cash NUMERIC(18, 4) DEFAULT 0.0000,
    reserved_asset NUMERIC(18, 8) DEFAULT 0.00000000
);

-- Users & Authentication
CREATE TABLE users (
    id VARCHAR(50) PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'USER' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Persistent Orders
CREATE TABLE orders (
    id VARCHAR(100) PRIMARY KEY,
    symbol VARCHAR(50) DEFAULT 'BTC-USD' NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    side VARCHAR(10) NOT NULL,
    order_type VARCHAR(10) DEFAULT 'LIMIT' NOT NULL,
    price NUMERIC(18,4),
    qty BIGINT NOT NULL,
    filled_qty BIGINT DEFAULT 0 NOT NULL,
    status VARCHAR(20) DEFAULT 'OPEN' NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Settlements & Idempotency
CREATE TABLE settlements (
    trade_id VARCHAR(64) PRIMARY KEY,
    settled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Double-Entry Ledger
CREATE TABLE ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    trade_id VARCHAR(64) NOT NULL REFERENCES settlements(trade_id) ON DELETE RESTRICT,
    user_id VARCHAR(64) NOT NULL REFERENCES accounts(user_id) ON DELETE RESTRICT,
    currency VARCHAR(10) NOT NULL CHECK (currency IN ('USD', 'BTC')),
    amount NUMERIC(20, 8) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Audit Trail
CREATE TABLE trade_audits (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    trade_id VARCHAR(100) NOT NULL,
    buy_order_id VARCHAR(100) NOT NULL,
    sell_order_id VARCHAR(100) NOT NULL,
    buyer_id VARCHAR(50) NOT NULL,
    seller_id VARCHAR(50) NOT NULL,
    price NUMERIC(18,4) NOT NULL,
    qty BIGINT NOT NULL,
    match_reason TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 5. REST & Streaming API Reference

### 5.1 Authentication
* `POST /api/auth/register` — Register a new account (`username`, `password`).
* `POST /api/auth/login` — Authenticate user and receive JWT access token.

### 5.2 Order Management & Execution
* `POST /api/orders` — Submit new order (`symbol`, `userId`, `side`, `orderType`, `price`, `qty`).
* `DELETE /api/orders/{orderId}?userId={userId}` — Cancel open order and release reserved funds.
* `GET /api/orders/user/{userId}` — Retrieve historical and active orders for a user.
* `GET /api/orderbook?symbol={symbol}` — Fetch Level-2 market depth snapshot.

### 5.3 Ledger, Portfolio & Audit
* `GET /api/wallet/{userId}` — Query cash, asset, available, and reserved balances.
* `GET /api/portfolio/{userId}` — Query total equity, holdings value, and P&L metrics.
* `GET /api/audit/trades` — Fetch match execution proofs.
* `GET /api/leaderboard` — Query trader net worth ranking.

### 5.4 System Administration & Diagnostics
* `POST /api/admin/risk/halt` — Trigger emergency global trading halt.
* `POST /api/admin/risk/resume` — Resume global trading.
* `POST /api/admin/system/bots/start` — Enable simulation bots.
* `POST /api/admin/system/bots/stop` — Disable simulation bots.
* `GET /api/admin/system/ledger/invariants` — Run platform-wide zero-sum balance invariant check.
* `POST /api/admin/system/crash-test` — Run consistency and crash proof verification.

---

## 6. Verification & Invariant Proofs

The platform provides automated verification endpoints to guarantee mathematical and transactional soundness:

### 6.1 Zero-Sum Invariant Query
```sql
SELECT currency, SUM(amount) AS net_sum 
FROM ledger_entries 
GROUP BY currency 
HAVING SUM(amount) <> 0;
```
If this query returns zero rows, the double-entry accounting invariant holds perfectly across the entire system.

### 6.2 Crash-Test Integrity Verification
The `/api/admin/system/crash-test` endpoint executes a three-way cross-check:
1. Validates that `SUM(amount) = 0` per currency in `ledger_entries`.
2. Asserts that `COUNT(settlements) = COUNT(trade_audits)`.
3. Verifies that every settled trade has exactly 4 corresponding `ledger_entries`.

---

## 7. Conclusion

TradeCore demonstrates an architecture capable of combining strict financial ledger guarantees with low-latency matching engine performance. By coupling single-threaded fair locking at the order book level with lexicographically ordered pessimistic locking at the database settlement layer, TradeCore ensures zero race conditions, zero deadlocks, and total double-entry ledger auditability.
