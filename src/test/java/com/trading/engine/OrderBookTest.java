package com.trading.engine;

import com.trading.common.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    private static int seq = 0;
    private static synchronized Order o(String user, Side s, String price, long qty) {
        return new Order("o" + (++seq), user, s, new BigDecimal(price), qty, Instant.now());
    }

    @Test void noMatchRestsOnBook() {
        var e = new OrderBookMatchingEngine();
        assertTrue(e.submit(o("alice", Side.BUY, "99", 10)).isEmpty());
        assertTrue(e.submit(o("bob", Side.SELL, "101", 10)).isEmpty());
        assertEquals(new BigDecimal("99"), e.snapshot().bids().get(0).price());
        assertEquals(new BigDecimal("101"), e.snapshot().asks().get(0).price());
    }

    @Test void fullFill() {
        var e = new OrderBookMatchingEngine();
        e.submit(o("bob", Side.SELL, "100", 10));
        List<Trade> t = e.submit(o("alice", Side.BUY, "100", 10));
        assertEquals(1, t.size());
        assertEquals(10, t.get(0).qty());
        assertEquals("alice", t.get(0).buyerId());
        assertEquals("bob", t.get(0).sellerId());
        assertTrue(e.snapshot().asks().isEmpty());
        assertTrue(e.snapshot().bids().isEmpty());
    }

    @Test void partialFillRestsRemainder() {
        var e = new OrderBookMatchingEngine();
        e.submit(o("bob", Side.SELL, "100", 4));
        List<Trade> t = e.submit(o("alice", Side.BUY, "100", 10));
        assertEquals(1, t.size());
        assertEquals(4, t.get(0).qty());
        assertEquals(6, e.snapshot().bids().get(0).qty());   // remainder rests
    }

    @Test void executesAtRestingOrderPrice() {
        var e = new OrderBookMatchingEngine();
        e.submit(o("bob", Side.SELL, "100", 5));
        List<Trade> t = e.submit(o("alice", Side.BUY, "105", 5)); // willing to pay 105
        assertEquals(new BigDecimal("100"), t.get(0).price());     // but trades at 100
    }

    @Test void priceThenTimePriority() {
        var e = new OrderBookMatchingEngine();
        Order first  = o("bob",   Side.SELL, "100", 5);
        Order second = o("carol", Side.SELL, "100", 5);   // same price, later
        Order better = o("dave",  Side.SELL, "99",  5);   // better price, latest
        e.submit(first); e.submit(second); e.submit(better);

        List<Trade> t = e.submit(o("alice", Side.BUY, "100", 12));
        assertEquals(3, t.size());
        assertEquals("dave",  t.get(0).sellerId());  // best price first
        assertEquals("bob",   t.get(1).sellerId());  // then earliest at same price
        assertEquals("carol", t.get(2).sellerId());
        assertEquals(2, t.get(2).qty());
    }

    @Test void sweepsMultipleLevelsButStopsAtLimit() {
        var e = new OrderBookMatchingEngine();
        e.submit(o("b1", Side.SELL, "100", 5));
        e.submit(o("b2", Side.SELL, "101", 5));
        e.submit(o("b3", Side.SELL, "105", 5));
        List<Trade> t = e.submit(o("alice", Side.BUY, "102", 20));
        assertEquals(2, t.size());                              // 105 not crossed
        assertEquals(10, e.snapshot().bids().get(0).qty());     // 20 - 10 rests
    }

    @Test void cancelRemovesOrder() {
        var e = new OrderBookMatchingEngine();
        Order b = o("alice", Side.BUY, "99", 5);
        e.submit(b);
        assertTrue(e.cancel(b.id()));
        assertTrue(e.snapshot().bids().isEmpty());
        assertFalse(e.cancel("nope"));
    }

    @Test void rejectsInvalidOrder() {
        var e = new OrderBookMatchingEngine();
        assertThrows(IllegalArgumentException.class, () -> e.submit(o("a", Side.BUY, "100", 0)));
    }

    /** Concurrency: 8 threads x 500 orders. Total bought must equal total sold, no over-fills. */
    @Test void concurrentSubmissionsNeverOverfill() throws Exception {
        var e = new OrderBookMatchingEngine();
        int threads = 8, perThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicLong tradedQty = new AtomicLong();
        AtomicLong submittedBuy = new AtomicLong(), submittedSell = new AtomicLong();
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> fs = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int id = t;
            fs.add(pool.submit(() -> {
                go.await();
                for (int i = 0; i < perThread; i++) {
                    Side s = (id % 2 == 0) ? Side.BUY : Side.SELL;
                    long q = 1 + (i % 5);
                    (s == Side.BUY ? submittedBuy : submittedSell).addAndGet(q);
                    e.submit(o("u" + id, s, "100", q)).forEach(tr -> tradedQty.addAndGet(tr.qty()));
                }
                return null;
            }));
        }
        go.countDown();
        for (Future<?> f : fs) f.get();
        pool.shutdown();

        long restingBuy  = e.snapshot().bids().stream().mapToLong(OrderBook.Level::qty).sum();
        long restingSell = e.snapshot().asks().stream().mapToLong(OrderBook.Level::qty).sum();
        assertEquals(submittedBuy.get(),  tradedQty.get() + restingBuy);   // conservation of quantity
        assertEquals(submittedSell.get(), tradedQty.get() + restingSell);
        assertTrue(restingBuy == 0 || restingSell == 0);                    // book never left crossed
    }
}
