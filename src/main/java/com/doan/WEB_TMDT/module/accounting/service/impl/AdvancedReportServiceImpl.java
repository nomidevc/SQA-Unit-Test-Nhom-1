package com.doan.WEB_TMDT.module.accounting.service.impl;

import com.doan.WEB_TMDT.module.accounting.dto.AdvancedReportRequest;
import com.doan.WEB_TMDT.module.accounting.dto.AdvancedReportResponse;
import com.doan.WEB_TMDT.module.accounting.entity.TransactionCategory;
import com.doan.WEB_TMDT.module.accounting.entity.TransactionType;
import com.doan.WEB_TMDT.module.accounting.repository.FinancialTransactionRepository;
import com.doan.WEB_TMDT.module.accounting.service.AdvancedReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdvancedReportServiceImpl implements AdvancedReportService {

    private final FinancialTransactionRepository transactionRepository;

    @Override
    public AdvancedReportResponse generateProfitLossReport(AdvancedReportRequest request) {
        LocalDateTime startDate = LocalDate.parse(request.getStartDate()).atStartOfDay();
        LocalDateTime endDate = LocalDate.parse(request.getEndDate()).atTime(23, 59, 59);

        // Calculate revenue
        Double salesRevenue = transactionRepository.sumAmountByTypeAndDateRange(
                TransactionType.REVENUE, startDate, endDate);
        if (salesRevenue == null)
            salesRevenue = 0.0;

        // Calculate expenses by category
        Double shippingCosts = getExpenseByCategory(TransactionCategory.SHIPPING, startDate, endDate);
        Double taxExpense = getExpenseByCategory(TransactionCategory.TAX, startDate, endDate);
        Double supplierPayments = getExpenseByCategory(TransactionCategory.SUPPLIER_PAYMENT, startDate, endDate);
        Double otherExpense = getExpenseByCategory(TransactionCategory.OTHER_EXPENSE, startDate, endDate);

        // Total expense
        Double totalExpense = shippingCosts + taxExpense + supplierPayments + otherExpense;

        // Calculate profit
        Double grossProfit = salesRevenue - totalExpense;
        Double grossProfitMargin = salesRevenue > 0 ? (grossProfit / salesRevenue) * 100 : 0.0;

        // Calculate VAT (10% of revenue)
        Double vatAmount = salesRevenue * 0.1;

        // Net profit after VAT
        Double netProfit = grossProfit - vatAmount;
        Double netProfitMargin = salesRevenue > 0 ? (netProfit / salesRevenue) * 100 : 0.0;

        String period = formatPeriod(request.getStartDate(), request.getEndDate());

        return AdvancedReportResponse.builder()
                .period(period)
                .reportType("PROFIT_LOSS")
                .salesRevenue(salesRevenue)
                .otherRevenue(0.0)
                .totalRevenue(salesRevenue)
                .shippingCosts(shippingCosts)
                .taxExpense(taxExpense)
                .supplierPayments(supplierPayments)
                .otherExpense(otherExpense)
                .totalExpense(totalExpense)
                .grossProfit(grossProfit)
                .grossProfitMargin(grossProfitMargin)
                .netProfit(netProfit)
                .netProfitMargin(netProfitMargin)
                .vatAmount(vatAmount)
                .build();
    }

    @Override
    public AdvancedReportResponse generateCashFlowReport(AdvancedReportRequest request) {
        LocalDateTime startDate = LocalDate.parse(request.getStartDate()).atStartOfDay();
        LocalDateTime endDate = LocalDate.parse(request.getEndDate()).atTime(23, 59, 59);

        // Operating activities
        Double operatingCashIn = transactionRepository.sumAmountByTypeAndDateRange(
                TransactionType.REVENUE, startDate, endDate);
        if (operatingCashIn == null)
            operatingCashIn = 0.0;

        Double operatingCashOut = transactionRepository.sumAmountByTypeAndDateRange(
                TransactionType.EXPENSE, startDate, endDate);
        if (operatingCashOut == null)
            operatingCashOut = 0.0;

        Double netOperatingCash = operatingCashIn - operatingCashOut;

        // Investing activities (currently 0)
        Double investingCashFlow = 0.0;

        // Financing activities (currently 0)
        Double financingCashFlow = 0.0;

        // Net cash flow
        Double netCashFlow = netOperatingCash + investingCashFlow + financingCashFlow;

        String period = formatPeriod(request.getStartDate(), request.getEndDate());

        return AdvancedReportResponse.builder()
                .period(period)
                .reportType("CASH_FLOW")
                .operatingCashIn(operatingCashIn)
                .operatingCashOut(operatingCashOut)
                .netOperatingCash(netOperatingCash)
                .investingCashFlow(investingCashFlow)
                .financingCashFlow(financingCashFlow)
                .netCashFlow(netCashFlow)
                .build();
    }

    @Override
    public AdvancedReportResponse generateExpenseAnalysis(AdvancedReportRequest request) {
        LocalDateTime startDate = LocalDate.parse(request.getStartDate()).atStartOfDay();
        LocalDateTime endDate = LocalDate.parse(request.getEndDate()).atTime(23, 59, 59);

        // Get total expense
        Double totalExpense = transactionRepository.sumAmountByTypeAndDateRange(
                TransactionType.EXPENSE, startDate, endDate);
        if (totalExpense == null || totalExpense == 0.0) {
            totalExpense = 0.0;
        }

        // Calculate expense by category
        Map<String, Double> expenseByCategory = new HashMap<>();
        expenseByCategory.put("Vận chuyển", getExpenseByCategory(TransactionCategory.SHIPPING, startDate, endDate));
        expenseByCategory.put("Thuế", getExpenseByCategory(TransactionCategory.TAX, startDate, endDate));
        expenseByCategory.put("Thanh toán NCC",
                getExpenseByCategory(TransactionCategory.SUPPLIER_PAYMENT, startDate, endDate));
        expenseByCategory.put("Chi phí khác",
                getExpenseByCategory(TransactionCategory.OTHER_EXPENSE, startDate, endDate));

        // Build breakdown list
        List<AdvancedReportResponse.ExpenseBreakdown> breakdown = new ArrayList<>();
        for (Map.Entry<String, Double> entry : expenseByCategory.entrySet()) {
            if (entry.getValue() > 0) {
                Double percentage = totalExpense > 0 ? (entry.getValue() / totalExpense) * 100 : 0.0;
                breakdown.add(AdvancedReportResponse.ExpenseBreakdown.builder()
                        .category(entry.getKey())
                        .amount(entry.getValue())
                        .percentage(percentage)
                        .build());
            }
        }

        String period = formatPeriod(request.getStartDate(), request.getEndDate());

        return AdvancedReportResponse.builder()
                .period(period)
                .reportType("EXPENSE_ANALYSIS")
                .totalExpense(totalExpense)
                .breakdown(breakdown)
                .build();
    }

    private Double getExpenseByCategory(TransactionCategory category, LocalDateTime startDate, LocalDateTime endDate) {
        var transactions = transactionRepository.findByTransactionDateBetween(startDate, endDate);
        return transactions.stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE && t.getCategory() == category)
                .mapToDouble(t -> t.getAmount())
                .sum();
    }

    private String formatPeriod(String startDate, String endDate) {
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        LocalDate start = LocalDate.parse(startDate, inputFormatter);
        LocalDate end = LocalDate.parse(endDate, inputFormatter);

        return start.format(outputFormatter) + " - " + end.format(outputFormatter);
    }
}
