package com.doan.WEB_TMDT.module.accounting.service.impl;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.accounting.dto.AccountingPeriodResponse;
import com.doan.WEB_TMDT.module.accounting.entity.AccountingPeriod;
import com.doan.WEB_TMDT.module.accounting.entity.PeriodStatus;
import com.doan.WEB_TMDT.module.accounting.entity.TransactionType;
import com.doan.WEB_TMDT.module.accounting.repository.AccountingPeriodRepository;
import com.doan.WEB_TMDT.module.accounting.repository.FinancialTransactionRepository;
import com.doan.WEB_TMDT.module.accounting.service.AccountingPeriodService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountingPeriodServiceImpl implements AccountingPeriodService {

    private final AccountingPeriodRepository periodRepository;
    private final FinancialTransactionRepository transactionRepository;

    @Override
    public ApiResponse getAllPeriods() {
        List<AccountingPeriod> periods = periodRepository.findAllByOrderByStartDateDesc();
        List<AccountingPeriodResponse> response = periods.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ApiResponse.success("Lấy danh sách kỳ kế toán thành công", response);
    }

    @Override
    public ApiResponse getPeriodById(Long id) {
        AccountingPeriod period = periodRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kỳ kế toán"));
        return ApiResponse.success("Lấy thông tin kỳ kế toán thành công", toResponse(period));
    }

    @Override
    @Transactional
    public ApiResponse createPeriod(String name, String startDateStr, String endDateStr) {
        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);

        if (periodRepository.existsByStartDateAndEndDate(startDate, endDate)) {
            return ApiResponse.error("Kỳ kế toán này đã tồn tại");
        }

        AccountingPeriod period = AccountingPeriod.builder()
                .name(name)
                .startDate(startDate)
                .endDate(endDate)
                .status(PeriodStatus.OPEN)
                .build();

        // Calculate stats
        calculateStats(period);

        AccountingPeriod saved = periodRepository.save(period);
        return ApiResponse.success("Tạo kỳ kế toán thành công", toResponse(saved));
    }

    @Override
    @Transactional
    public ApiResponse closePeriod(Long id, String closedBy) {
        AccountingPeriod period = periodRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kỳ kế toán"));

        if (period.getStatus() == PeriodStatus.CLOSED) {
            return ApiResponse.error("Kỳ kế toán này đã được chốt");
        }

        // Recalculate stats before closing
        calculateStats(period);

        // Check discrepancy rate
        if (period.getDiscrepancyRate() != null && period.getDiscrepancyRate() > 15.0) {
            return ApiResponse.error("Không thể chốt kỳ khi sai số > 15%");
        }

        period.setStatus(PeriodStatus.CLOSED);
        period.setClosedAt(LocalDateTime.now());
        period.setClosedBy(closedBy);

        AccountingPeriod updated = periodRepository.save(period);
        return ApiResponse.success("Chốt kỳ kế toán thành công", toResponse(updated));
    }

    @Override
    @Transactional
    public ApiResponse reopenPeriod(Long id) {
        AccountingPeriod period = periodRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kỳ kế toán"));

        if (period.getStatus() == PeriodStatus.OPEN) {
            return ApiResponse.error("Kỳ kế toán này đang mở");
        }

        period.setStatus(PeriodStatus.OPEN);
        period.setClosedAt(null);
        period.setClosedBy(null);

        AccountingPeriod updated = periodRepository.save(period);
        return ApiResponse.success("Mở khóa kỳ kế toán thành công", toResponse(updated));
    }

    @Override
    @Transactional
    public ApiResponse calculatePeriodStats(Long id) {
        AccountingPeriod period = periodRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kỳ kế toán"));

        calculateStats(period);
        AccountingPeriod updated = periodRepository.save(period);

        return ApiResponse.success("Tính toán thống kê kỳ thành công", toResponse(updated));
    }

    @Override
    public ApiResponse getPeriodDetails(Long id) {
        AccountingPeriod period = periodRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kỳ kế toán"));

        LocalDateTime startDateTime = period.getStartDate().atStartOfDay();
        LocalDateTime endDateTime = period.getEndDate().atTime(LocalTime.MAX);

        // Lấy tất cả giao dịch trong kỳ
        List<com.doan.WEB_TMDT.module.accounting.entity.FinancialTransaction> allTransactions = transactionRepository
                .findByTransactionDateBetween(startDateTime, endDateTime);

        // Phân loại giao dịch
        List<com.doan.WEB_TMDT.module.accounting.entity.FinancialTransaction> revenueTransactions = allTransactions
                .stream()
                .filter(t -> t.getType() == TransactionType.REVENUE)
                .collect(Collectors.toList());

        List<com.doan.WEB_TMDT.module.accounting.entity.FinancialTransaction> expenseTransactions = allTransactions
                .stream()
                .filter(t -> t.getType() == TransactionType.EXPENSE)
                .collect(Collectors.toList());

        // Tính doanh thu theo chuẩn kế toán
        Double salesRevenue = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.REVENUE &&
                        t.getCategory() == com.doan.WEB_TMDT.module.accounting.entity.TransactionCategory.SALES)
                .mapToDouble(com.doan.WEB_TMDT.module.accounting.entity.FinancialTransaction::getAmount)
                .sum();

        Double refundAmount = allTransactions.stream()
                .filter(t -> t.getType() == TransactionType.REFUND)
                .mapToDouble(com.doan.WEB_TMDT.module.accounting.entity.FinancialTransaction::getAmount)
                .sum();

        Double netRevenue = salesRevenue - refundAmount; // Doanh thu thuần
        Double profitMargin = netRevenue > 0 ? (period.getNetProfit() / netRevenue) * 100 : 0.0; // Biên lợi nhuận (%)

        // Tạo response
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("period", toResponse(period));
        details.put("revenueTransactions", revenueTransactions);
        details.put("expenseTransactions", expenseTransactions);
        details.put("totalTransactions", allTransactions.size());

        // Thêm chỉ số kế toán
        details.put("salesRevenue", salesRevenue); // Doanh thu gộp (bán hàng)
        details.put("refundAmount", refundAmount); // Hàng bán bị trả lại
        details.put("netRevenue", netRevenue); // Doanh thu thuần
        details.put("profitMargin", profitMargin); // Biên lợi nhuận (%)

        return ApiResponse.success("Lấy chi tiết kỳ kế toán thành công", details);
    }

    private void calculateStats(AccountingPeriod period) {
        LocalDateTime startDateTime = period.getStartDate().atStartOfDay();
        LocalDateTime endDateTime = period.getEndDate().atTime(LocalTime.MAX);

        // Calculate revenue
        Double revenue = transactionRepository.sumAmountByTypeAndDateRange(
                TransactionType.REVENUE, startDateTime, endDateTime);
        period.setTotalRevenue(revenue != null ? revenue : 0.0);

        // Calculate expense
        Double expense = transactionRepository.sumAmountByTypeAndDateRange(
                TransactionType.EXPENSE, startDateTime, endDateTime);
        period.setTotalExpense(expense != null ? expense : 0.0);

        // Calculate net profit
        period.setNetProfit(period.getTotalRevenue() - period.getTotalExpense());

        // Calculate discrepancy rate (simplified - compare with expected)
        // In real system, this would compare with bank statements
        double expectedRevenue = period.getTotalRevenue();
        double actualRevenue = period.getTotalRevenue(); // Would come from bank reconciliation

        if (expectedRevenue > 0) {
            double discrepancy = Math.abs(expectedRevenue - actualRevenue);
            period.setDiscrepancyRate((discrepancy / expectedRevenue) * 100);
        } else {
            period.setDiscrepancyRate(0.0);
        }
    }

    private AccountingPeriodResponse toResponse(AccountingPeriod period) {
        return AccountingPeriodResponse.builder()
                .id(period.getId())
                .name(period.getName())
                .startDate(period.getStartDate())
                .endDate(period.getEndDate())
                .status(period.getStatus())
                .totalRevenue(period.getTotalRevenue())
                .totalExpense(period.getTotalExpense())
                .netProfit(period.getNetProfit())
                .discrepancyRate(period.getDiscrepancyRate())
                .closedAt(period.getClosedAt())
                .closedBy(period.getClosedBy())
                .build();
    }
}
