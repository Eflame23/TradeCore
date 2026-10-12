package com.tradecore.risk.position;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Position provider querying current holdings directly from the database schema
 * (the accounts table created in Part B: Ledger & Settlement).
 */
@Component
public class DatabasePositionProvider implements PositionProvider {

    private static final Logger log = LoggerFactory.getLogger(DatabasePositionProvider.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabasePositionProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<BigDecimal> getPosition(String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }

        try {
            List<BigDecimal> results = jdbcTemplate.query(
                    "SELECT asset_balance FROM accounts WHERE user_id = ?",
                    (rs, rowNum) -> rs.getBigDecimal("asset_balance"),
                    userId
            );

            if (results.isEmpty()) {
                log.debug("No account entry found in accounts table for user: {}", userId);
                return Optional.empty();
            }

            return Optional.ofNullable(results.get(0));
        } catch (DataAccessException e) {
            log.error("Database query failed while fetching position for user {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1;
        } catch (Exception e) {
            log.warn("DatabasePositionProvider unavailable: {}", e.getMessage());
            return false;
        }
    }
}
