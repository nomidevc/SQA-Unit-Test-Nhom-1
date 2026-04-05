package com.doan.WEB_TMDT.module.accounting.repository;

import com.doan.WEB_TMDT.module.accounting.entity.TaxReport;
import com.doan.WEB_TMDT.module.accounting.entity.TaxStatus;
import com.doan.WEB_TMDT.module.accounting.entity.TaxType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaxReportRepository extends JpaRepository<TaxReport, Long> {
    
    List<TaxReport> findAllByOrderByPeriodStartDesc();
    
    List<TaxReport> findByTaxType(TaxType taxType);
    
    List<TaxReport> findByStatus(TaxStatus status);
    
    boolean existsByReportCode(String reportCode);
    
    java.util.Optional<TaxReport> findByReportCode(String reportCode);
    
    @Query("SELECT SUM(t.remainingTax) FROM TaxReport t WHERE t.status != 'PAID'")
    Double sumRemainingTax();
    
    @Query("SELECT SUM(t.remainingTax) FROM TaxReport t WHERE t.taxType = :taxType AND t.status != 'PAID'")
    Double sumRemainingTaxByType(@Param("taxType") TaxType taxType);
    
    @Query("SELECT SUM(t.paidAmount) FROM TaxReport t WHERE t.taxType = :taxType")
    Double sumPaidAmountByType(@Param("taxType") TaxType taxType);
}
