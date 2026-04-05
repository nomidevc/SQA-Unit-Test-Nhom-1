package com.doan.WEB_TMDT.module.review.service;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.review.dto.CreateReviewRequest;

public interface ProductReviewService {
    
    // Helper method để lấy customerId từ email
    Long getCustomerIdByEmail(String email);
    
    // Khách hàng tạo đánh giá sau khi nhận hàng hoặc comment thường
    ApiResponse createReview(CreateReviewRequest request, Long customerId);
    
    // Lấy danh sách đánh giá của sản phẩm
    ApiResponse getReviewsByProduct(Long productId);
    
    // Lấy đánh giá trung bình và số lượng đánh giá
    ApiResponse getProductRatingSummary(Long productId);
    
    // Lấy danh sách đánh giá của khách hàng
    ApiResponse getReviewsByCustomer(Long customerId);
    
    // Kiểm tra khách hàng đã đánh giá sản phẩm trong đơn hàng chưa
    ApiResponse checkCanReview(Long orderId, Long productId, Long customerId);
    
    // Admin xóa review/comment
    ApiResponse deleteReview(Long reviewId);
}
