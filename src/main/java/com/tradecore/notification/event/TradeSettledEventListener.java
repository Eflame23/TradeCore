package com.tradecore.notification.event;

import com.tradecore.notification.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Writes notifications only after the settlement transaction commits successfully. */
@Component
public class TradeSettledEventListener {
    private final NotificationService notificationService;

    public TradeSettledEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTradeSettled(TradeSettledEvent event) {
        notificationService.createTradeSettledNotifications(event);
    }
}
