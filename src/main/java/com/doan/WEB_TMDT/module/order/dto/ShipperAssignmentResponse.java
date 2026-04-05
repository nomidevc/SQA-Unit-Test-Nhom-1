package com.doan.WEB_TMDT.module.order.dto;

import com.doan.WEB_TMDT.module.order.entity.ShipperAssignmentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ShipperAssignmentResponse {
    private Long id;
    private Long orderId;
    private String orderCode;
    private Long shipperId;
    private String shipperName;
    private String shipperPhone;
    private ShipperAssignmentStatus status;
    private LocalDateTime claimedAt;
    private LocalDateTime deliveringAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime failedAt;
    private String failureReason;
    private String shipperNote;
    
    // Thông tin đơn hàng
    private String customerName;
    private String customerPhone;
    private String shippingAddress;
    private Double total;
}
