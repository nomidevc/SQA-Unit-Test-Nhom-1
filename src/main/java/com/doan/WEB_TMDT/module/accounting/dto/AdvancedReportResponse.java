package com.doan.WEB_TMDT.module.accounting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdvancedReportResponse {
    private String period;
    private String reportType;

    // Profit & Loss Report
    private Double salesRevenue;
    private Double otherRevenue;
    private Double totalRevenue;
    private Double shippingCosts;
    private Double taxExpense;
    private Double supplierPayments;
    private Double otherExpense;
    private Double totalExpense;
    private Double grossProfit;
    private Double grossProfitMargin;
    private Double netProfit;
    private Double netProfitMargin;
    private Double vatAmount;

    // Cash Flow Report
    private Double operatingCashIn;
    private Double operatingCashOut;
    private Double netOperatingCash;
    private Double investingCashFlow;
    private Double financingCashFlow;
    private Double netCashFlow;

    // Expense Analysis
    private List<ExpenseBreakdown> breakdown;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExpenseBreakdown {
        private String category;
        private Double amount;
        private Double percentage;
    }
}
