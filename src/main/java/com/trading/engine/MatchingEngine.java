package com.trading.engine;

import com.trading.common.Order;
import com.trading.common.Trade;
import java.util.List;

/** Shared contract: the only thing other modules see of the engine. */
public interface MatchingEngine {
    List<Trade> submit(Order order);
}
