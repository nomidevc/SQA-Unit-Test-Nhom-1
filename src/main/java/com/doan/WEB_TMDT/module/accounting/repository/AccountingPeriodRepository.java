package com.doan.WEB_TMDT.module.accounting.repository;

import com.doan.WEB_TMDT.module.accounting.entity.AccountingPeriod;
import com.doan.WEB_TMDT.module.accounting.entity.PeriodStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, Long> {
    
    List<AccountingPeriod> findAllByOrderByStartDateDesc();
    
    List<AccountingPeriod> findByStatus(PeriodStatus status);
    
    Optional<AccountingPeriod> findByStartDateAndEndDate(LocalDate startDate, LocalDate endDate);
    
    boolean existsByStartDateAndEndDate(LocalDate startDate, LocalDate endDate);
}
