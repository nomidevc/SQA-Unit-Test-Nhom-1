package com.doan.WEB_TMDT.module.accounting.repository;

import com.doan.WEB_TMDT.module.accounting.entity.FinancialTransaction;
import com.doan.WEB_TMDT.module.accounting.entity.TransactionType;
import com.doan.WEB_TMDT.module.accounting.entity.TransactionCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Long> {
    
    Page<FinancialTransaction> findAllByOrderByTransactionDateDesc(Pageable pageable);
    
    List<FinancialTransaction> findByTransactionDateBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    List<FinancialTransaction> findByTypeAndTransactionDateBetween(
        TransactionType type, 
        LocalDateTime startDate, 
        LocalDateTime endDate
    );
    
    @Query("SELECT SUM(t.amount) FROM FinancialTransaction t " +
           "WHERE t.type = :type AND t.transactionDate BETWEEN :startDate AND :endDate")
    Double sumAmountByTypeAndDateRange(
        @Param("type") TransactionType type,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    // Methods for automation
    boolean existsByOrderIdAndType(Long orderId, TransactionType type);
    
    boolean existsByOrderIdAndCategory(Long orderId, TransactionCategory category);
    
    List<FinancialTransaction> findByOrderId(Long orderId);
    
    List<FinancialTransaction> findBySupplierId(Long supplierId);
    
    @Query("SELECT SUM(t.amount) FROM FinancialTransaction t " +
           "WHERE t.type = :type AND t.category = :category " +
           "AND t.transactionDate BETWEEN :startDate AND :endDate")
    Double sumByTypeAndCategoryAndDateBetween(
        @Param("type") TransactionType type,
        @Param("category") TransactionCategory category,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
}
