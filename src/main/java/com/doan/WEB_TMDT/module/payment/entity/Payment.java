package com.doan.WEB_TMDT.module.payment.entity;

import com.doan.WEB_TMDT.module.auth.entity.User;
import com.doan.WEB_TMDT.module.order.entity.Order;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true, nullable = false)
    private String paymentCode; // Mã thanh toán: PAY20231119001
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Column(nullable = false)
    private Double amount; // Số tiền thanh toán
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method; // SEPAY
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;
    
    // SePay specific fields
    private String sepayTransactionId; // Mã giao dịch từ SePay
    private String sepayBankCode; // Mã ngân hàng
    private String sepayAccountNumber; // Số tài khoản
    private String sepayAccountName; // Tên tài khoản
    private String sepayContent; // Nội dung chuyển khoản
    private String sepayQrCode; // URL QR Code
    
    @Column(columnDefinition = "TEXT")
    private String sepayResponse; // Response từ SePay (JSON)
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime paidAt; // Thời gian thanh toán thành công
    
    private LocalDateTime expiredAt; // Thời gian hết hạn (15 phút)
    
    private String failureReason; // Lý do thất bại
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        expiredAt = createdAt.plusMinutes(1); // Hết hạn sau 15 phút
        if (status == null) {
            status = PaymentStatus.PENDING;
        }
        if (method == null) {
            method = PaymentMethod.SEPAY;
        }
    }
}
