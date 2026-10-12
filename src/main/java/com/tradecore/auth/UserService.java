package com.tradecore.auth;

import com.tradecore.ledger.service.WalletService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final WalletService walletService;

    public UserService(JdbcTemplate jdbcTemplate,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       WalletService walletService) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.walletService = walletService;
    }

    public AuthResponse register(String username, String password) {
        String userId = username.toLowerCase().trim();
        List<User> existing = findByUsername(userId);
        if (!existing.isEmpty()) {
            throw new IllegalArgumentException("Username already exists");
        }

        String hash = passwordEncoder.encode(password);
        String role = "admin".equalsIgnoreCase(userId) ? "ADMIN" : "USER";

        jdbcTemplate.update(
                "INSERT INTO users (id, username, password_hash, role) VALUES (?, ?, ?, ?)",
                userId, userId, hash, role
        );

        // Seed wallet balance for new registered user
        walletService.getWallet(userId);

        String token = jwtTokenProvider.createToken(userId, userId, role);
        return new AuthResponse(userId, userId, role, token);
    }

    public AuthResponse login(String username, String password) {
        String userId = username.toLowerCase().trim();
        List<User> existing = findByUsername(userId);
        if (existing.isEmpty()) {
            // Auto-provision if default user/bot exists in seed accounts
            if ("alice".equalsIgnoreCase(userId) || "bob".equalsIgnoreCase(userId) || "admin".equalsIgnoreCase(userId)) {
                return register(userId, "password123");
            }
            throw new IllegalArgumentException("Invalid username or password");
        }

        User user = existing.get(0);
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new IllegalArgumentException("Invalid username or password");
        }

        String token = jwtTokenProvider.createToken(user.id(), user.username(), user.role());
        return new AuthResponse(user.id(), user.username(), user.role(), token);
    }

    public List<User> findByUsername(String username) {
        return jdbcTemplate.query(
                "SELECT id, username, password_hash, role, created_at FROM users WHERE username = ?",
                (rs, rowNum) -> new User(
                        rs.getString("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("role"),
                        rs.getTimestamp("created_at").toInstant()
                ),
                username
        );
    }

    public record AuthResponse(
            String userId,
            String username,
            String role,
            String token
    ) {}
}
