package com.doan.WEB_TMDT.module.review.repository;

import com.doan.WEB_TMDT.module.review.entity.ProductReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductReviewRepository extends JpaRepository<ProductReview, Long> {
    
    List<ProductReview> findByProductIdOrderByCreatedAtDesc(Long productId);
    
    List<ProductReview> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
    
    Optional<ProductReview> findByOrderIdAndProductId(Long orderId, Long productId);
    
    boolean existsByOrderIdAndProductId(Long orderId, Long productId);
    
    @Query("SELECT AVG(r.rating) FROM ProductReview r WHERE r.product.id = :productId")
    Double getAverageRatingByProductId(Long productId);
    
    @Query("SELECT COUNT(r) FROM ProductReview r WHERE r.product.id = :productId")
    Long getReviewCountByProductId(Long productId);
    
    @Query("SELECT r.rating, COUNT(r) FROM ProductReview r WHERE r.product.id = :productId GROUP BY r.rating")
    List<Object[]> getRatingDistributionByProductId(Long productId);
}
