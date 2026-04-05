package com.doan.WEB_TMDT.module.review.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ProductReviewResponse {
    private Long id;
    private Long productId;
    private String productName;
    private Long customerId;
    private String customerName;
    private Long orderId;
    private String orderCode;
    private Integer rating;
    private String comment;
    private Boolean isVerifiedPurchase; // true nếu đã mua hàng
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
