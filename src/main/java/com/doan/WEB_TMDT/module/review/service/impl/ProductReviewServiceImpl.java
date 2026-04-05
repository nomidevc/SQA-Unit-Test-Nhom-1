package com.doan.WEB_TMDT.module.review.service.impl;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.auth.entity.Customer;
import com.doan.WEB_TMDT.module.auth.repository.CustomerRepository;
import com.doan.WEB_TMDT.module.order.entity.Order;
import com.doan.WEB_TMDT.module.order.entity.OrderStatus;
import com.doan.WEB_TMDT.module.order.repository.OrderRepository;
import com.doan.WEB_TMDT.module.product.entity.Product;
import com.doan.WEB_TMDT.module.product.repository.ProductRepository;
import com.doan.WEB_TMDT.module.review.dto.CreateReviewRequest;
import com.doan.WEB_TMDT.module.review.dto.ProductReviewResponse;
import com.doan.WEB_TMDT.module.review.entity.ProductReview;
import com.doan.WEB_TMDT.module.review.repository.ProductReviewRepository;
import com.doan.WEB_TMDT.module.review.service.ProductReviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductReviewServiceImpl implements ProductReviewService {
    
    private final ProductReviewRepository reviewRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerRepository customerRepository;
    
    @Override
    public Long getCustomerIdByEmail(String email) {
        return customerRepository.findByUserEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khách hàng với email: " + email))
                .getId();
    }
    
    @Override
    @Transactional
    public ApiResponse createReview(CreateReviewRequest request, Long customerId) {
        // Lấy thông tin sản phẩm và khách hàng
        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm"));
        
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khách hàng"));
        
        Order order = null;
        
        // Nếu có orderId - đây là đánh giá sau khi mua hàng
        if (request.getOrderId() != null) {
            // Kiểm tra rating phải có
            if (request.getRating() == null) {
                return ApiResponse.error("Vui lòng chọn số sao đánh giá");
            }
            
            // Kiểm tra đơn hàng
            order = orderRepository.findById(request.getOrderId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));
            
            // Kiểm tra quyền sở hữu đơn hàng
            if (!order.getCustomer().getId().equals(customerId)) {
                return ApiResponse.error("Bạn không có quyền đánh giá đơn hàng này");
            }
            
            // Kiểm tra đơn hàng đã giao thành công chưa
            if (order.getStatus() != OrderStatus.DELIVERED && order.getStatus() != OrderStatus.COMPLETED) {
                return ApiResponse.error("Chỉ có thể đánh giá sau khi đã nhận hàng");
            }
            
            // Kiểm tra sản phẩm có trong đơn hàng không
            boolean productInOrder = order.getItems().stream()
                    .anyMatch(item -> item.getProduct().getId().equals(request.getProductId()));
            
            if (!productInOrder) {
                return ApiResponse.error("Sản phẩm không có trong đơn hàng này");
            }
            
            // Kiểm tra đã đánh giá chưa
            if (reviewRepository.existsByOrderIdAndProductId(request.getOrderId(), request.getProductId())) {
                return ApiResponse.error("Bạn đã đánh giá sản phẩm này rồi");
            }
        } else {
            // Đây là comment thường - không cần orderId, rating sẽ là null
            // Không cần kiểm tra gì thêm
        }
        
        // Tạo đánh giá/comment
        ProductReview review = ProductReview.builder()
                .product(product)
                .customer(customer)
                .order(order)
                .rating(request.getRating()) // Có thể null nếu là comment thường
                .comment(request.getComment())
                .build();
        
        reviewRepository.save(review);
        
        if (order != null) {
            log.info("Customer {} reviewed product {} with {} stars", customerId, request.getProductId(), request.getRating());
        } else {
            log.info("Customer {} commented on product {}", customerId, request.getProductId());
        }
        
        return ApiResponse.success(order != null ? "Đánh giá thành công" : "Bình luận thành công", toResponse(review));
    }
    
    @Override
    public ApiResponse getReviewsByProduct(Long productId) {
        List<ProductReview> reviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(productId);
        List<ProductReviewResponse> responses = reviews.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ApiResponse.success("Danh sách đánh giá", responses);
    }
    
    @Override
    public ApiResponse getProductRatingSummary(Long productId) {
        Double avgRating = reviewRepository.getAverageRatingByProductId(productId);
        Long reviewCount = reviewRepository.getReviewCountByProductId(productId);
        List<Object[]> distribution = reviewRepository.getRatingDistributionByProductId(productId);
        
        // Build rating distribution map
        Map<Integer, Long> ratingDistribution = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            ratingDistribution.put(i, 0L);
        }
        for (Object[] row : distribution) {
            Integer rating = (Integer) row[0];
            Long count = (Long) row[1];
            ratingDistribution.put(rating, count);
        }
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("averageRating", avgRating != null ? Math.round(avgRating * 10.0) / 10.0 : 0.0);
        summary.put("reviewCount", reviewCount != null ? reviewCount : 0);
        summary.put("ratingDistribution", ratingDistribution);
        
        return ApiResponse.success("Thống kê đánh giá", summary);
    }
    
    @Override
    public ApiResponse getReviewsByCustomer(Long customerId) {
        List<ProductReview> reviews = reviewRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        List<ProductReviewResponse> responses = reviews.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ApiResponse.success("Danh sách đánh giá của bạn", responses);
    }
    
    @Override
    public ApiResponse checkCanReview(Long orderId, Long productId, Long customerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));
        
        // Kiểm tra quyền sở hữu
        if (!order.getCustomer().getId().equals(customerId)) {
            return ApiResponse.error("Không có quyền");
        }
        
        // Kiểm tra trạng thái
        if (order.getStatus() != OrderStatus.DELIVERED) {
            return ApiResponse.error("Đơn hàng chưa giao thành công");
        }
        
        // Kiểm tra đã đánh giá chưa
        boolean reviewed = reviewRepository.existsByOrderIdAndProductId(orderId, productId);
        
        Map<String, Object> result = new HashMap<>();
        result.put("canReview", !reviewed);
        result.put("reviewed", reviewed);
        
        return ApiResponse.success("Kiểm tra quyền đánh giá", result);
    }
    
    @Override
    @Transactional
    public ApiResponse deleteReview(Long reviewId) {
        ProductReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đánh giá"));
        
        reviewRepository.delete(review);
        
        log.info("Admin deleted review {}", reviewId);
        
        return ApiResponse.success("Xóa đánh giá thành công", null);
    }
    
    private ProductReviewResponse toResponse(ProductReview review) {
        return ProductReviewResponse.builder()
                .id(review.getId())
                .productId(review.getProduct().getId())
                .productName(review.getProduct().getName())
                .customerId(review.getCustomer().getId())
                .customerName(review.getCustomer().getFullName())
                .orderId(review.getOrder() != null ? review.getOrder().getId() : null)
                .orderCode(review.getOrder() != null ? review.getOrder().getOrderCode() : null)
                .rating(review.getRating())
                .comment(review.getComment())
                .isVerifiedPurchase(review.getIsVerifiedPurchase())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}
