package com.doan.WEB_TMDT.module.order.repository;

import com.doan.WEB_TMDT.module.order.entity.Order;
import com.doan.WEB_TMDT.module.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderCode(String orderCode);
    Optional<Order> findByGhnOrderCode(String ghnOrderCode);
    List<Order> findByCustomerId(Long customerId);
    List<Order> findByStatus(OrderStatus status);
    List<Order> findByCustomerIdAndStatus(Long customerId, OrderStatus status);
    boolean existsByOrderCode(String orderCode);
    
    // Accounting queries
    List<Order> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    @Query("SELECT o FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate " +
           "AND o.paymentStatus = com.doan.WEB_TMDT.module.order.entity.PaymentStatus.PAID")
    List<Order> findPaidOrdersBetween(@Param("startDate") LocalDateTime startDate, 
                                      @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT SUM(o.total) FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate " +
           "AND o.paymentStatus = com.doan.WEB_TMDT.module.order.entity.PaymentStatus.PAID")
    Double sumTotalByDateRange(@Param("startDate") LocalDateTime startDate, 
                               @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT COUNT(o) FROM Order o WHERE o.createdAt BETWEEN :startDate AND :endDate " +
           "AND o.paymentStatus = com.doan.WEB_TMDT.module.order.entity.PaymentStatus.PAID")
    Long countPaidOrdersBetween(@Param("startDate") LocalDateTime startDate, 
                                @Param("endDate") LocalDateTime endDate);
    
    // Warehouse queries - Lấy đơn CONFIRMED chưa xuất kho
    @Query("SELECT o FROM Order o WHERE o.status = :status " +
           "AND NOT EXISTS (SELECT 1 FROM ExportOrder e WHERE e.orderId = o.id) " +
           "ORDER BY o.confirmedAt DESC")
    List<Order> findByStatusAndNotExported(@Param("status") OrderStatus status);
    
    // Dashboard queries
    Long countByStatus(OrderStatus status);
    Long countByCreatedAtAfter(LocalDateTime date);
    List<Order> findByCreatedAtAfter(LocalDateTime date);
    
    // Customer queries
    Long countByCustomerId(Long customerId);
    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
