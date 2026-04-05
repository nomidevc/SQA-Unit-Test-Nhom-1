package com.doan.WEB_TMDT.module.review.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.review.dto.CreateReviewRequest;
import com.doan.WEB_TMDT.module.review.service.ProductReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ProductReviewController {
    
    private final ProductReviewService reviewService;
    
    // Khách hàng tạo đánh giá
    @PostMapping
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ApiResponse createReview(
            @Valid @RequestBody CreateReviewRequest request,
            Authentication authentication) {
        Long customerId = getCustomerIdFromAuth(authentication);
        return reviewService.createReview(request, customerId);
    }
    
    // Lấy danh sách đánh giá của sản phẩm (public)
    @GetMapping("/product/{productId}")
    public ApiResponse getReviewsByProduct(@PathVariable Long productId) {
        return reviewService.getReviewsByProduct(productId);
    }
    
    // Lấy thống kê đánh giá sản phẩm (public)
    @GetMapping("/product/{productId}/summary")
    public ApiResponse getProductRatingSummary(@PathVariable Long productId) {
        return reviewService.getProductRatingSummary(productId);
    }
    
    // Lấy danh sách đánh giá của khách hàng
    @GetMapping("/my-reviews")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ApiResponse getMyReviews(Authentication authentication) {
        Long customerId = getCustomerIdFromAuth(authentication);
        return reviewService.getReviewsByCustomer(customerId);
    }
    
    // Kiểm tra có thể đánh giá không
    @GetMapping("/can-review")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ApiResponse checkCanReview(
            @RequestParam Long orderId,
            @RequestParam Long productId,
            Authentication authentication) {
        Long customerId = getCustomerIdFromAuth(authentication);
        return reviewService.checkCanReview(orderId, productId, customerId);
    }
    
    // Admin xóa review/comment
    @DeleteMapping("/{reviewId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ApiResponse deleteReview(@PathVariable Long reviewId) {
        return reviewService.deleteReview(reviewId);
    }
    
    private Long getCustomerIdFromAuth(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new RuntimeException("Không tìm thấy thông tin xác thực");
        }
        String email = authentication.getName();
        return reviewService.getCustomerIdByEmail(email);
    }
}
