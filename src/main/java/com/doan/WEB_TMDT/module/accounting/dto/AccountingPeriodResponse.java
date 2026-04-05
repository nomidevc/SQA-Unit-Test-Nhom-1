package com.doan.WEB_TMDT.module.accounting.dto;

import com.doan.WEB_TMDT.module.accounting.entity.PeriodStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class AccountingPeriodResponse {
    private Long id;
    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private PeriodStatus status;
    private Double totalRevenue;
    private Double totalExpense;
    private Double netProfit;
    private Double discrepancyRate;
    private LocalDateTime closedAt;
    private String closedBy;
}
