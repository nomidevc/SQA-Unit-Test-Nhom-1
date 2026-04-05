package com.doan.WEB_TMDT.module.order.entity;

import com.doan.WEB_TMDT.module.auth.entity.Customer;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String orderCode; // Mã đơn hàng: ORD20231119001
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items;
    
    // Thông tin giao hàng
    @Column(nullable = false, columnDefinition = "TEXT")
    private String shippingAddress;
    
    // Địa chỉ chi tiết (for GHN integration)
    private String province;  // Tỉnh/Thành phố
    private String district;  // Quận/Huyện
    private String ward;      // Phường/Xã ward code (for GHN API)
    private String wardName;  // Tên phường/xã (for display)
    private String address;   // Địa chỉ cụ thể (số nhà, tên đường)
    
    private String note; // Ghi chú của khách hàng
    
    // Giá tiền
    @Column(nullable = false)
    private Double subtotal; // Tổng tiền hàng
    
    @Column(nullable = false)
    private Double shippingFee; // Phí vận chuyển
    
    @Column(nullable = false)
    private Double discount; // Giảm giá
    
    @Column(nullable = false)
    private Double total; // Tổng thanh toán
    
    // Thanh toán (chỉ lưu trạng thái, chi tiết ở Payment module)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;
    
    @Column(length = 20)
    private String paymentMethod; // COD, SEPAY, VNPAY, etc.
    
    private Long paymentId; // Reference đến Payment entity
    
    // Note: paidAt được lấy từ Payment entity (nếu có)
    
    // Trạng thái đơn hàng
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime confirmedAt; // Xác nhận đơn
    
    private LocalDateTime shippedAt; // Giao hàng
    
    private LocalDateTime deliveredAt; // Đã giao
    
    private LocalDateTime cancelledAt; // Hủy đơn
    
    private String cancelReason; // Lý do hủy
    
    private LocalDateTime completedAt; // Hoàn thành (khách xác nhận nhận hàng)
    
    // GHN Shipping Integration
    private String ghnOrderCode; // Mã vận đơn GHN
    
    private String ghnShippingStatus; // Trạng thái vận chuyển từ GHN
    
    private LocalDateTime ghnCreatedAt; // Thời gian tạo đơn GHN
    
    private LocalDateTime ghnExpectedDeliveryTime; // Thời gian giao hàng dự kiến
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = OrderStatus.PENDING_PAYMENT; // Default cho đơn online
        }
        if (paymentStatus == null) {
            paymentStatus = PaymentStatus.UNPAID;
        }
    }
}
