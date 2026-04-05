package com.doan.WEB_TMDT.module.order.entity;

import com.doan.WEB_TMDT.module.auth.entity.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "shipper_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipperAssignment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Version
    private Long version; // Optimistic locking để tránh nhiều shipper nhận cùng 1 đơn
    
    @OneToOne
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipper_id", nullable = false)
    private Employee shipper; // Nhân viên có position = SHIPPER
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShipperAssignmentStatus status;
    
    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt; // Thời gian tạo assignment
    
    @Column(nullable = false)
    private LocalDateTime claimedAt; // Thời gian shipper nhận đơn
    
    private LocalDateTime deliveringAt; // Đã lấy hàng và bắt đầu giao
    
    private LocalDateTime deliveredAt; // Đã giao thành công
    
    private LocalDateTime failedAt; // Giao thất bại
    
    private String failureReason; // Lý do giao thất bại
    
    private String shipperNote; // Ghi chú của shipper
    
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (assignedAt == null) {
            assignedAt = now;
        }
        if (claimedAt == null) {
            claimedAt = now;
        }
        if (status == null) {
            status = ShipperAssignmentStatus.CLAIMED;
        }
    }
}
