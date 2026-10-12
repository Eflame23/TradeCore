package com.trading.engine;

import com.trading.common.Order;
import com.trading.common.Side;
import com.trading.common.Trade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Limit order book with PRICE-TIME priority.
 *
 *  - bids: TreeMap sorted HIGH -> LOW   (best bid = firstKey)
 *  - asks: TreeMap sorted LOW  -> HIGH  (best ask = firstKey)
 *  - each price level is a FIFO Deque  => time priority inside a price
 *
 * TreeMap is a red-black tree: best-price lookup / insert / remove are O(log P),
 * where P = number of distinct price levels.
 *
 * NOT thread-safe by itself. OrderBookMatchingEngine wraps it with a lock.
 */
public class OrderBook {

    /** Mutable wrapper so we can track the unfilled quantity (Order itself is immutable). */
    static final class Resting {
        final Order order;
        long remaining;
        Resting(Order order) { this.order = order; this.remaining = order.qty(); }
    }

    private final TreeMap<BigDecimal, Deque<Resting>> bids = new TreeMap<>(Comparator.reverseOrder());
    private final TreeMap<BigDecimal, Deque<Resting>> asks = new TreeMap<>();

    /** Match an incoming order against the book; rest any unfilled remainder. */
    public List<Trade> match(Order incoming) {
        if (incoming.qty() <= 0) throw new IllegalArgumentException("qty must be > 0");
        if (incoming.price() == null || incoming.price().signum() <= 0)
            throw new IllegalArgumentException("price must be > 0");

        boolean isBuy = incoming.side() == Side.BUY;
        TreeMap<BigDecimal, Deque<Resting>> opposite = isBuy ? asks : bids;
        Resting taker = new Resting(incoming);
        List<Trade> trades = new ArrayList<>();

        while (taker.remaining > 0 && !opposite.isEmpty()) {
            Map.Entry<BigDecimal, Deque<Resting>> best = opposite.firstEntry();
            BigDecimal bestPrice = best.getKey();

            // Does the incoming limit price cross the best opposite price?
            boolean crosses = isBuy ? incoming.price().compareTo(bestPrice) >= 0
                                    : incoming.price().compareTo(bestPrice) <= 0;
            if (!crosses) break;

            Deque<Resting> level = best.getValue();
            while (taker.remaining > 0 && !level.isEmpty()) {
                Resting maker = level.peekFirst();               // oldest first = time priority
                long fillQty = Math.min(taker.remaining, maker.remaining);

                // Known limitation: self-trade prevention not implemented in the prototype.
                trades.add(buildTrade(taker.order, maker.order, bestPrice, fillQty)); // resting order's price
                taker.remaining -= fillQty;
                maker.remaining -= fillQty;
                if (maker.remaining == 0) level.pollFirst();
            }
            if (level.isEmpty()) opposite.remove(bestPrice);     // drop empty price level
        }

        // Rest the unfilled remainder on our own side of the book
        if (taker.remaining > 0) {
            TreeMap<BigDecimal, Deque<Resting>> own = isBuy ? bids : asks;
            own.computeIfAbsent(incoming.price(), p -> new ArrayDeque<>()).addLast(taker);
        }
        return trades;
    }

    private Trade buildTrade(Order taker, Order maker, BigDecimal price, long qty) {
        boolean takerBuys = taker.side() == Side.BUY;
        Order buy  = takerBuys ? taker : maker;
        Order sell = takerBuys ? maker : taker;
        return new Trade(UUID.randomUUID().toString(), buy.id(), sell.id(),
                         buy.userId(), sell.userId(), price, qty, Instant.now());
    }

    /** Cancel a resting order by id. Returns true if found. O(n) - fine for prototype. */
    public boolean cancel(String orderId) {
        return removeFrom(bids, orderId) || removeFrom(asks, orderId);
    }

    private boolean removeFrom(TreeMap<BigDecimal, Deque<Resting>> side, String orderId) {
        for (Iterator<Map.Entry<BigDecimal, Deque<Resting>>> it = side.entrySet().iterator(); it.hasNext();) {
            Map.Entry<BigDecimal, Deque<Resting>> e = it.next();
            if (e.getValue().removeIf(r -> r.order.id().equals(orderId))) {
                if (e.getValue().isEmpty()) it.remove();
                return true;
            }
        }
        return false;
    }

    public Optional<BigDecimal> bestBid() { return bids.isEmpty() ? Optional.empty() : Optional.of(bids.firstKey()); }
    public Optional<BigDecimal> bestAsk() { return asks.isEmpty() ? Optional.empty() : Optional.of(asks.firstKey()); }

    /** Read-only snapshot for GET /orderbook: [price, totalRemainingQty], best first. */
    public Snapshot snapshot() {
        return new Snapshot(levels(bids), levels(asks));
    }

    private List<Level> levels(TreeMap<BigDecimal, Deque<Resting>> side) {
        List<Level> out = new ArrayList<>();
        side.forEach((price, q) -> out.add(new Level(price, q.stream().mapToLong(r -> r.remaining).sum())));
        return out;
    }

    public record Level(BigDecimal price, long qty) {}
    public record Snapshot(List<Level> bids, List<Level> asks) {}
}
