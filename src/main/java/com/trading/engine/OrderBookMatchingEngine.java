package com.trading.engine;

import com.trading.common.Order;
import com.trading.common.Trade;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe MatchingEngine for ONE instrument (e.g. BTC-USD).
 *
 * WHY A SINGLE LOCK: matching is a read-modify-write on shared state
 * (best price, queue heads). Two orders matching at once could fill the same
 * resting order twice. One lock makes submit() atomic and, because it is fair,
 * preserves arrival order (time priority). Real exchanges go further with a
 * single-threaded event loop (LMAX Disruptor style).
 */
@Service
public class OrderBookMatchingEngine implements MatchingEngine {

    private final OrderBook book = new OrderBook();
    private final ReentrantLock lock = new ReentrantLock(true); // fair = FIFO for waiting threads

    @Override
    public List<Trade> submit(Order order) {
        lock.lock();
        try {
            return book.match(order);
        } finally {
            lock.unlock();
        }
    }

    public boolean cancel(String orderId) {
        lock.lock();
        try { return book.cancel(orderId); } finally { lock.unlock(); }
    }

    public OrderBook.Snapshot snapshot() {
        lock.lock();
        try { return book.snapshot(); } finally { lock.unlock(); }
    }
}
