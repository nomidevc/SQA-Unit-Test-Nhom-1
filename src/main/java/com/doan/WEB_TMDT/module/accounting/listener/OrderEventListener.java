package com.doan.WEB_TMDT.module.accounting.listener;

import com.doan.WEB_TMDT.module.order.entity.Order;
import com.doan.WEB_TMDT.module.order.entity.OrderStatus;
import com.doan.WEB_TMDT.module.order.entity.PaymentStatus;
import com.doan.WEB_TMDT.module.accounting.entity.FinancialTransaction;
import com.doan.WEB_TMDT.module.accounting.entity.TransactionCategory;
import com.doan.WEB_TMDT.module.accounting.entity.TransactionType;
import com.doan.WEB_TMDT.module.accounting.repository.FinancialTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Listener để tự động tạo giao dịch kế toán khi có sự kiện đơn hàng
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventListener {

    private final FinancialTransactionRepository transactionRepository;

    /**
     * Listen to order status change events and create accounting transactions
     */
    @EventListener
    @Transactional
    public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
        Order order = event.getOrder();
        OrderStatus oldStatus = event.getOldStatus();
        OrderStatus newStatus = event.getNewStatus();

        log.info("Handling order status change: {} -> {} for order {}",
                oldStatus, newStatus, order.getId());

        // When order is CONFIRMED and PAID -> Create revenue transaction
        if (newStatus == OrderStatus.CONFIRMED && order.getPaymentStatus() == PaymentStatus.PAID) {
            onOrderPaid(order);
        }

        // When order is DELIVERED or COMPLETED -> Create shipping expense
        if (newStatus == OrderStatus.DELIVERED || newStatus == OrderStatus.COMPLETED) {
            onOrderCompleted(order);
        }
    }

    /**
     * Tự động tạo giao dịch khi đơn hàng được thanh toán
     */
    private void onOrderPaid(Order order) {
        try {
            // Kiểm tra xem đã tạo giao dịch cho đơn hàng này chưa
            boolean exists = transactionRepository.existsByOrderIdAndType(
                    order.getId(),
                    TransactionType.REVENUE);

            if (exists) {
                log.info("Transaction already exists for order {}", order.getId());
                return;
            }

            // 1. Tạo giao dịch DOANH THU
            createRevenueTransaction(order);

            log.info("✅ Created accounting transactions for order {}", order.getId());
        } catch (Exception e) {
            log.error("Error creating transactions for order {}: {}", order.getId(), e.getMessage());
        }
    }

    /**
     * Tự động tạo giao dịch chi phí vận chuyển khi đơn hàng hoàn thành
     */
    private void onOrderCompleted(Order order) {
        try {
            // Kiểm tra xem đã tạo giao dịch vận chuyển chưa
            boolean exists = transactionRepository.existsByOrderIdAndCategory(
                    order.getId(),
                    TransactionCategory.SHIPPING);

            if (exists) {
                log.info("Shipping transaction already exists for order {}", order.getId());
                return;
            }

            // Tạo giao dịch CHI PHÍ VẬN CHUYỂN
            createShippingExpenseTransaction(order);

            log.info("✅ Created shipping expense transaction for order {}", order.getId());
        } catch (Exception e) {
            log.error("Error creating shipping transaction for order {}: {}", order.getId(), e.getMessage());
        }
    }

    /**
     * Tạo giao dịch doanh thu từ đơn hàng
     */
    private void createRevenueTransaction(Order order) {
        FinancialTransaction transaction = FinancialTransaction.builder()
                .type(TransactionType.REVENUE)
                .category(TransactionCategory.SALES)
                .amount(order.getTotal())
                .orderId(order.getId())
                .description("Doanh thu từ đơn hàng #" + order.getOrderCode())
                .transactionDate(LocalDateTime.now())
                .createdBy("SYSTEM")
                .build();

        transactionRepository.save(transaction);
        log.info("Created REVENUE transaction: {} VND for order {}", order.getTotal(), order.getOrderCode());
    }

    /**
     * Tạo giao dịch chi phí vận chuyển
     * Chi phí thực tế = 80% phí vận chuyển thu từ khách
     */
    private void createShippingExpenseTransaction(Order order) {
        if (order.getShippingFee() == null || order.getShippingFee() <= 0) {
            return;
        }

        // Chi phí thực tế = 80% phí thu từ khách
        Double actualCost = order.getShippingFee() * 0.8;

        FinancialTransaction transaction = FinancialTransaction.builder()
                .type(TransactionType.EXPENSE)
                .category(TransactionCategory.SHIPPING)
                .amount(actualCost)
                .orderId(order.getId())
                .description("Chi phí vận chuyển đơn hàng #" + order.getOrderCode())
                .transactionDate(LocalDateTime.now())
                .createdBy("SYSTEM")
                .build();

        transactionRepository.save(transaction);
        log.info("Created SHIPPING EXPENSE transaction: {} VND for order {}", actualCost, order.getOrderCode());
    }

}
