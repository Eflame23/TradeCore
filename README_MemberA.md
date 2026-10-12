# Member A - Matching Engine

## What it does
Accepts limit orders for one instrument and matches buys against sells using
**price-time priority**. Returns a `List<Trade>`; any unfilled remainder rests on the book.

## Core design decision (be ready to defend this)
**Data structure:** `TreeMap<BigDecimal, Deque<Resting>>` per side.
- TreeMap (red-black tree) -> best price, insert, remove in **O(log P)**, P = distinct price levels.
- Bids use `Comparator.reverseOrder()` so `firstKey()` is always the highest bid.
- Each price level is an `ArrayDeque` (FIFO) -> **time priority** within a price, O(1) head removal.

**Alternatives rejected**
| Alternative | Why not |
|---|---|
| PriorityQueue<Order> | O(n) removal of arbitrary orders (cancel); cannot group by price level for a book snapshot |
| Sorted ArrayList | O(n) insert |
| HashMap of price -> queue | no ordered best-price lookup |

**Concurrency:** one fair `ReentrantLock` around `submit()`.
- Matching is read-modify-write on shared state; two threads could fill the same resting order twice.
- Fair lock = waiting threads served FIFO, which preserves arrival order (time priority).
- Trade-off: serializes all orders -> throughput ceiling. Fine because matching is CPU-light (microseconds).
- Production alternative: single-threaded event loop / LMAX Disruptor; one engine per instrument for scaling.

**Trade price rule:** execution happens at the *resting* (maker) order's price.

## Known limitations (state these honestly)
- Limit orders only, one instrument.
- No self-trade prevention (a user can match their own order).
- `cancel()` is O(n); a `Map<orderId, Resting>` index would make it O(1).
- In-memory only: book is lost on restart (the project plan uses Redis snapshots later).
- Matching and settlement are separate steps: if settlement fails after matching, the trade needs compensation (owned by integration/ledger).

## Test results
9 unit tests: no match, full fill, partial fill, price-time priority, multi-level sweep,
execution price, cancel, invalid input, **8-thread concurrency test** (quantity conservation,
book never left crossed).

## How to run
    mvn test                                  # run tests
    mvn compile exec:java -Dexec.mainClass=com.trading.Demo
(or just open the folder in IntelliJ and run `Demo.main` / the test class)

## Integration contract (for Member E)
    MatchingEngine engine = new OrderBookMatchingEngine();
    List<Trade> trades = engine.submit(order);
    engine.snapshot();   // for GET /orderbook
