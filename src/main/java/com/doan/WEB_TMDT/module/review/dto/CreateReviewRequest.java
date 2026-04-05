package com.doan.WEB_TMDT.module.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateReviewRequest {
    
    @NotNull(message = "Product ID không được để trống")
    private Long productId;
    
    // Nullable - nếu null thì là comment thường (không cần mua hàng)
    private Long orderId;
    
    // Nullable - chỉ bắt buộc khi có orderId
    @Min(value = 1, message = "Rating phải từ 1-5")
    @Max(value = 5, message = "Rating phải từ 1-5")
    private Integer rating;
    
    @NotBlank(message = "Nội dung comment không được để trống")
    private String comment; // Bắt buộc
}
