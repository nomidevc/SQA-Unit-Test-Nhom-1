package com.doan.WEB_TMDT.module.accounting.dto;

import com.doan.WEB_TMDT.module.accounting.entity.TaxStatus;
import com.doan.WEB_TMDT.module.accounting.entity.TaxType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class TaxReportResponse {
    private Long id;
    private String reportCode;
    private TaxType taxType;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Double taxableRevenue;
    private Double taxRate;
    private Double taxAmount;
    private Double paidAmount;
    private Double remainingTax;
    private TaxStatus status;
    private LocalDateTime submittedAt;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private String createdBy;
}
