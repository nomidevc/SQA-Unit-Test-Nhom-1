package com.doan.WEB_TMDT.scheduler;

import com.doan.WEB_TMDT.module.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled tasks cho payment
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentScheduler {

    private final PaymentService paymentService;

    /**
     * Tự động hết hạn các payment cũ
     * Chạy mỗi 1 phút để đảm bảo hủy đơn kịp thời
     */
    @Scheduled(fixedRate = 15000) // 15 seconds
    public void expireOldPayments() {
        log.info("=== SCHEDULER RUNNING: expireOldPayments at {} ===", java.time.LocalDateTime.now());
        try {
            paymentService.expireOldPayments();
        } catch (Exception e) {
            log.error("Error in expireOldPayments scheduled task", e);
        }
    }
}
