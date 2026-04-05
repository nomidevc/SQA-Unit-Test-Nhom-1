package com.doan.WEB_TMDT.module.review.entity;

import com.doan.WEB_TMDT.module.auth.entity.Customer;
import com.doan.WEB_TMDT.module.order.entity.Order;
import com.doan.WEB_TMDT.module.product.entity.Product;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductReview {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order; // Nullable - nếu null thì là comment thường
    
    @Column
    private Integer rating; // 1-5 sao, nullable - chỉ có khi mua hàng
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String comment; // Nội dung comment/đánh giá
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @Column(name = "is_verified_purchase")
    @Builder.Default
    private Boolean isVerifiedPurchase = false; // true nếu đã mua hàng
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (order != null) {
            isVerifiedPurchase = true;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
