package com.tradecore.audit.web;

import com.tradecore.audit.AuditService;
import com.tradecore.audit.TradeAudit;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/trades")
    public ResponseEntity<List<TradeAudit>> getRecentAudits(@RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(auditService.getRecentAudits(limit));
    }
}
