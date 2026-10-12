package com.tradecore.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final JdbcTemplate jdbcTemplate;

    public AuditService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordMatchAudit(String tradeId, String buyOrderId, String sellOrderId,
                                 String buyerId, String sellerId, BigDecimal price,
                                 long qty, String matchReason) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO trade_audits (trade_id, buy_order_id, sell_order_id, buyer_id, seller_id, price, qty, match_reason, created_at) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                    tradeId, buyOrderId, sellOrderId, buyerId, sellerId, price, qty, matchReason
            );
            log.info("Recorded audit log for trade {}: {}", tradeId, matchReason);
        } catch (Exception e) {
            log.error("Failed to record trade audit for trade {}: {}", tradeId, e.getMessage());
        }
    }

    public List<TradeAudit> getRecentAudits(int limit) {
        return jdbcTemplate.query(
                "SELECT id, trade_id, buy_order_id, sell_order_id, buyer_id, seller_id, price, qty, match_reason, created_at " +
                        "FROM trade_audits ORDER BY id DESC LIMIT ?",
                (rs, rowNum) -> new TradeAudit(
                        rs.getLong("id"),
                        rs.getString("trade_id"),
                        rs.getString("buy_order_id"),
                        rs.getString("sell_order_id"),
                        rs.getString("buyer_id"),
                        rs.getString("seller_id"),
                        rs.getBigDecimal("price"),
                        rs.getLong("qty"),
                        rs.getString("match_reason"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                limit
        );
    }
}
