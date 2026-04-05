package com.doan.WEB_TMDT.module.webhook.service.impl;

import com.doan.WEB_TMDT.module.order.entity.Order;
import com.doan.WEB_TMDT.module.order.entity.OrderStatus;
import com.doan.WEB_TMDT.module.order.entity.PaymentStatus;
import com.doan.WEB_TMDT.module.order.repository.OrderRepository;
import com.doan.WEB_TMDT.module.webhook.dto.GHNWebhookRequest;
import com.doan.WEB_TMDT.module.webhook.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookServiceImpl implements WebhookService {

    private final OrderRepository orderRepository;

    @Override
    @Transactional
    public void handleGHNWebhook(GHNWebhookRequest request) {
        String ghnOrderCode = request.getOrderCode();
        String ghnStatus = request.getStatus();
        
        log.info("Processing GHN webhook for order: {}, status: {}", ghnOrderCode, ghnStatus);
        
        // Find order by GHN order code
        Optional<Order> orderOpt = orderRepository.findByGhnOrderCode(ghnOrderCode);
        
        if (orderOpt.isEmpty()) {
            log.warn("Order not found for GHN order code: {}", ghnOrderCode);
            return;
        }
        
        Order order = orderOpt.get();
        log.info("Found order: {} ({})", order.getOrderCode(), order.getStatus());
        
        // Update GHN shipping status
        order.setGhnShippingStatus(ghnStatus);
        
        // Update order status based on GHN status
        updateOrderStatus(order, ghnStatus);
        
        // Save order
        orderRepository.save(order);
        
        log.info("✅ Updated order {} - Status: {}, GHN Status: {}", 
            order.getOrderCode(), order.getStatus(), ghnStatus);
    }
    
    private void updateOrderStatus(Order order, String ghnStatus) {
        LocalDateTime now = LocalDateTime.now();
        
        switch (ghnStatus) {
            case "ready_to_pick":
                // Chờ lấy hàng - GHN đã nhận đơn
                // Giữ nguyên READY_TO_SHIP (đã xuất kho, đợi tài xế)
                if (order.getStatus() == OrderStatus.CONFIRMED) {
                    order.setStatus(OrderStatus.READY_TO_SHIP);
                    if (order.getShippedAt() == null) {
                        order.setShippedAt(now);
                    }
                }
                break;
                
            case "picking":
                // Tài xế đang đến lấy hàng
                // Vẫn giữ READY_TO_SHIP
                if (order.getStatus() == OrderStatus.CONFIRMED) {
                    order.setStatus(OrderStatus.READY_TO_SHIP);
                    if (order.getShippedAt() == null) {
                        order.setShippedAt(now);
                    }
                }
                break;
                
            case "picked":
            case "storing":
            case "transporting":
            case "sorting":
                // ✅ Tài xế đã lấy hàng / Đang vận chuyển
                // Chuyển từ READY_TO_SHIP → SHIPPING
                if (order.getStatus() == OrderStatus.READY_TO_SHIP || 
                    order.getStatus() == OrderStatus.CONFIRMED || 
                    order.getStatus() == OrderStatus.PENDING_PAYMENT) {
                    order.setStatus(OrderStatus.SHIPPING);
                    if (order.getShippedAt() == null) {
                        order.setShippedAt(now);
                    }
                    log.info("🚚 Order {} status changed: READY_TO_SHIP → SHIPPING (driver picked up)", order.getOrderCode());
                }
                break;
                
            case "delivering":
            case "money_collect_delivering":
                // Đang giao hàng
                if (order.getStatus() != OrderStatus.SHIPPING) {
                    order.setStatus(OrderStatus.SHIPPING);
                    if (order.getShippedAt() == null) {
                        order.setShippedAt(now);
                    }
                }
                break;
                
            case "delivered":
                // Đã giao hàng thành công
                order.setStatus(OrderStatus.DELIVERED);
                if (order.getDeliveredAt() == null) {
                    order.setDeliveredAt(now);
                }
                // Mark as paid (COD collected)
                order.setPaymentStatus(PaymentStatus.PAID);
                log.info("✅ Order {} delivered successfully", order.getOrderCode());
                break;
                
            case "delivery_fail":
                // Giao hàng thất bại
                log.warn("⚠️ Delivery failed for order {}", order.getOrderCode());
                // Giữ nguyên SHIPPING, chờ giao lại
                break;
                
            case "waiting_to_return":
            case "return":
            case "return_transporting":
            case "return_sorting":
            case "returning":
                // Đang trả hàng
                log.warn("⚠️ Order {} is being returned", order.getOrderCode());
                // Có thể thêm status RETURNING nếu cần
                break;
                
            case "return_fail":
                // Trả hàng thất bại
                log.error("❌ Return failed for order {}", order.getOrderCode());
                break;
                
            case "returned":
                // Đã trả hàng về shop
                order.setStatus(OrderStatus.CANCELLED);
                if (order.getCancelledAt() == null) {
                    order.setCancelledAt(now);
                }
                order.setCancelReason("Trả hàng từ GHN");
                log.info("📦 Order {} returned to shop", order.getOrderCode());
                break;
                
            case "cancel":
                // Đơn bị hủy
                order.setStatus(OrderStatus.CANCELLED);
                if (order.getCancelledAt() == null) {
                    order.setCancelledAt(now);
                }
                order.setCancelReason("Hủy từ GHN");
                log.info("❌ Order {} cancelled by GHN", order.getOrderCode());
                break;
                
            case "exception":
            case "damage":
            case "lost":
                // Đơn hàng ngoại lệ / Hư hỏng / Thất lạc
                log.error("❌ Order {} has exception: {}", order.getOrderCode(), ghnStatus);
                // Có thể gửi notification cho admin
                break;
                
            default:
                log.warn("Unknown GHN status: {}", ghnStatus);
                break;
        }
    }
}
