package com.doan.WEB_TMDT.module.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long orderId;
    private String orderCode;
    private String status;
    private String paymentStatus;
    private String paymentMethod; // COD, SEPAY, VNPAY, etc.
    
    // Customer info
    private Long customerId;
    private String customerName;  // Từ Customer entity
    private String customerPhone; // Từ Customer entity
    private String customerEmail; // Từ User entity
    private String shippingAddress;
    
    // Detailed address (for warehouse)
    private String province;      // Tên tỉnh/thành phố
    private String district;      // Tên quận/huyện
    private String ward;          // Ward code (for GHN API)
    private String wardName;      // Tên phường/xã
    private String address;       // Địa chỉ cụ thể (số nhà, đường)
    
    private String note;
    
    // Items
    private List<OrderItemResponse> items;
    
    // Pricing
    private Double subtotal;
    private Double shippingFee;
    private Double discount;
    private Double total;
    
    // Timestamps
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime completedAt; // Khách xác nhận nhận hàng
    private LocalDateTime cancelledAt;
    private String cancelReason;
    
    // GHN Shipping info
    private String ghnOrderCode;
    private String ghnShippingStatus;
    private LocalDateTime ghnCreatedAt;
    private LocalDateTime ghnExpectedDeliveryTime;
    
    // Shipper info (for internal delivery)
    private Long shipperId;
    private String shipperName;
    private String shipperPhone;
}
