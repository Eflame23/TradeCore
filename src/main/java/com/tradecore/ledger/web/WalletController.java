package com.tradecore.ledger.web;

import com.tradecore.ledger.model.WalletDTO;
import com.tradecore.ledger.service.WalletService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/wallet")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/{userId}")
    public ResponseEntity<WalletDTO> getWallet(@PathVariable String userId) {
        return ResponseEntity.ok(walletService.getWallet(userId));
    }

    @PostMapping("/deposit")
    public ResponseEntity<WalletDTO> deposit(@RequestBody DepositRequest request) {
        WalletDTO updated = walletService.deposit(
                request.userId(),
                request.cashAmount() != null ? request.cashAmount() : BigDecimal.ZERO,
                request.assetAmount() != null ? request.assetAmount() : BigDecimal.ZERO
        );
        return ResponseEntity.ok(updated);
    }

    public record DepositRequest(String userId, BigDecimal cashAmount, BigDecimal assetAmount) {}
}
