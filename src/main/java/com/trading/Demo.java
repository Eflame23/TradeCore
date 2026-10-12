package com.trading;

import com.trading.common.*;
import com.trading.engine.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Run this for a console demo - no Spring/DB needed. */
public class Demo {
    static int n = 0;
    static Order o(String u, Side s, String p, long q) {
        return new Order("ORD-" + (++n), u, s, new BigDecimal(p), q, Instant.now());
    }
    public static void main(String[] a) {
        var engine = new OrderBookMatchingEngine();
        System.out.println(">> Bob SELL 10 @ 100");  System.out.println(engine.submit(o("bob", Side.SELL, "100", 10)));
        System.out.println(">> Carol SELL 5 @ 101"); System.out.println(engine.submit(o("carol", Side.SELL, "101", 5)));
        System.out.println(">> Alice BUY 12 @ 101 (sweeps two levels)");
        engine.submit(o("alice", Side.BUY, "101", 12)).forEach(t ->
            System.out.println("   TRADE " + t.qty() + " @ " + t.price() + "  " + t.buyerId() + " <- " + t.sellerId()));
        System.out.println("Book now: " + engine.snapshot());
    }
}
