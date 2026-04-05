package com.doan.WEB_TMDT.module.accounting.service.impl;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.accounting.dto.TaxReportRequest;
import com.doan.WEB_TMDT.module.accounting.dto.TaxReportResponse;
import com.doan.WEB_TMDT.module.accounting.dto.TaxSummaryResponse;
import com.doan.WEB_TMDT.module.accounting.entity.TaxReport;
import com.doan.WEB_TMDT.module.accounting.entity.TaxStatus;
import com.doan.WEB_TMDT.module.accounting.entity.TaxType;
import com.doan.WEB_TMDT.module.accounting.entity.TransactionType;
import com.doan.WEB_TMDT.module.accounting.repository.FinancialTransactionRepository;
import com.doan.WEB_TMDT.module.accounting.repository.TaxReportRepository;
import com.doan.WEB_TMDT.module.accounting.service.TaxReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaxReportServiceImpl implements TaxReportService {

    private final TaxReportRepository taxReportRepository;
    private final FinancialTransactionRepository financialTransactionRepo;

    @Override
    public ApiResponse getAllTaxReports() {
        List<TaxReport> reports = taxReportRepository.findAllByOrderByPeriodStartDesc();
        List<TaxReportResponse> response = reports.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ApiResponse.success("Lấy danh sách báo cáo thuế thành công", response);
    }

    @Override
    public ApiResponse getTaxReportsByType(TaxType taxType) {
        List<TaxReport> reports = taxReportRepository.findByTaxType(taxType);
        List<TaxReportResponse> response = reports.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ApiResponse.success("Lấy danh sách báo cáo thuế thành công", response);
    }

    @Override
    public ApiResponse getTaxReportById(Long id) {
        TaxReport report = taxReportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy báo cáo thuế"));

        // Lấy danh sách giao dịch doanh thu chịu thuế trong kỳ
        java.time.LocalDateTime startDateTime = report.getPeriodStart().atStartOfDay();
        java.time.LocalDateTime endDateTime = report.getPeriodEnd().atTime(java.time.LocalTime.MAX);

        java.util.List<com.doan.WEB_TMDT.module.accounting.entity.FinancialTransaction> taxableTransactions = financialTransactionRepo
                .findByTransactionDateBetween(startDateTime, endDateTime)
                .stream()
                .filter(t -> t.getType() == com.doan.WEB_TMDT.module.accounting.entity.TransactionType.REVENUE &&
                        t.getCategory() == com.doan.WEB_TMDT.module.accounting.entity.TransactionCategory.SALES)
                .collect(java.util.stream.Collectors.toList());

        // Tạo response với thông tin báo cáo và danh sách giao dịch
        java.util.Map<String, Object> details = new java.util.HashMap<>();
        details.put("report", toResponse(report));
        details.put("taxableTransactions", taxableTransactions);

        return ApiResponse.success("Lấy thông tin báo cáo thuế thành công", details);
    }

    @Override
    @Transactional
    public ApiResponse createTaxReport(TaxReportRequest request, String createdBy) {
        LocalDate periodStart = LocalDate.parse(request.getPeriodStart());
        LocalDate periodEnd = LocalDate.parse(request.getPeriodEnd());

        // Calculate tax amount
        Double taxAmount = request.getTaxableRevenue() * (request.getTaxRate() / 100);

        TaxReport report = TaxReport.builder()
                .taxType(request.getTaxType())
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .taxableRevenue(request.getTaxableRevenue())
                .taxRate(request.getTaxRate())
                .taxAmount(taxAmount)
                .paidAmount(0.0)
                .remainingTax(taxAmount)
                .status(TaxStatus.DRAFT)
                .createdBy(createdBy)
                .build();

        TaxReport saved = taxReportRepository.save(report);
        return ApiResponse.success("Tạo báo cáo thuế thành công", toResponse(saved));
    }

    @Override
    @Transactional
    public ApiResponse updateTaxReport(Long id, TaxReportRequest request) {
        TaxReport report = taxReportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy báo cáo thuế"));

        if (report.getStatus() != TaxStatus.DRAFT) {
            return ApiResponse.error("Chỉ có thể sửa báo cáo ở trạng thái nháp");
        }

        LocalDate periodStart = LocalDate.parse(request.getPeriodStart());
        LocalDate periodEnd = LocalDate.parse(request.getPeriodEnd());
        Double taxAmount = request.getTaxableRevenue() * (request.getTaxRate() / 100);

        report.setTaxType(request.getTaxType());
        report.setPeriodStart(periodStart);
        report.setPeriodEnd(periodEnd);
        report.setTaxableRevenue(request.getTaxableRevenue());
        report.setTaxRate(request.getTaxRate());
        report.setTaxAmount(taxAmount);
        report.setRemainingTax(taxAmount - (report.getPaidAmount() != null ? report.getPaidAmount() : 0.0));

        TaxReport updated = taxReportRepository.save(report);
        return ApiResponse.success("Cập nhật báo cáo thuế thành công", toResponse(updated));
    }

    @Override
    @Transactional
    public ApiResponse submitTaxReport(Long id) {
        TaxReport report = taxReportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy báo cáo thuế"));

        if (report.getStatus() != TaxStatus.DRAFT) {
            return ApiResponse.error("Báo cáo này đã được gửi");
        }

        report.setStatus(TaxStatus.SUBMITTED);
        report.setSubmittedAt(LocalDateTime.now());

        TaxReport updated = taxReportRepository.save(report);
        return ApiResponse.success("Gửi báo cáo thuế thành công", toResponse(updated));
    }

    @Override
    @Transactional
    public ApiResponse markAsPaid(Long id) {
        TaxReport report = taxReportRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy báo cáo thuế"));

        if (report.getStatus() == TaxStatus.PAID) {
            return ApiResponse.error("Báo cáo này đã được đánh dấu thanh toán");
        }

        report.setStatus(TaxStatus.PAID);
        report.setPaidAmount(report.getTaxAmount());
        report.setRemainingTax(0.0);
        report.setPaidAt(LocalDateTime.now());

        TaxReport updated = taxReportRepository.save(report);
        return ApiResponse.success("Đánh dấu đã thanh toán thành công", toResponse(updated));
    }

    @Override
    public ApiResponse getTaxSummary() {
        Double vatOwed = taxReportRepository.sumRemainingTaxByType(TaxType.VAT);
        Double vatPaid = taxReportRepository.sumPaidAmountByType(TaxType.VAT);
        Double corporateOwed = taxReportRepository.sumRemainingTaxByType(TaxType.CORPORATE_TAX);
        Double corporatePaid = taxReportRepository.sumPaidAmountByType(TaxType.CORPORATE_TAX);

        TaxSummaryResponse summary = TaxSummaryResponse.builder()
                .vatOwed(vatOwed != null ? vatOwed : 0.0)
                .vatPaid(vatPaid != null ? vatPaid : 0.0)
                .corporateOwed(corporateOwed != null ? corporateOwed : 0.0)
                .corporatePaid(corporatePaid != null ? corporatePaid : 0.0)
                .totalOwed((vatOwed != null ? vatOwed : 0.0) + (corporateOwed != null ? corporateOwed : 0.0))
                .totalPaid((vatPaid != null ? vatPaid : 0.0) + (corporatePaid != null ? corporatePaid : 0.0))
                .build();

        return ApiResponse.success("Lấy tổng quan thuế thành công", summary);
    }

    private TaxReportResponse toResponse(TaxReport report) {
        return TaxReportResponse.builder()
                .id(report.getId())
                .reportCode(report.getReportCode())
                .taxType(report.getTaxType())
                .periodStart(report.getPeriodStart())
                .periodEnd(report.getPeriodEnd())
                .taxableRevenue(report.getTaxableRevenue())
                .taxRate(report.getTaxRate())
                .taxAmount(report.getTaxAmount())
                .paidAmount(report.getPaidAmount())
                .remainingTax(report.getRemainingTax())
                .status(report.getStatus())
                .submittedAt(report.getSubmittedAt())
                .paidAt(report.getPaidAt())
                .createdAt(report.getCreatedAt())
                .createdBy(report.getCreatedBy())
                .build();
    }

    @Override
    public ApiResponse calculateTaxableRevenue(String periodStart, String periodEnd) {
        try {
            LocalDate startDate = LocalDate.parse(periodStart);
            LocalDate endDate = LocalDate.parse(periodEnd);

            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

            // Tính tổng doanh thu từ financial_transactions (REVENUE)
            Double totalRevenue = financialTransactionRepo.sumAmountByTypeAndDateRange(
                    TransactionType.REVENUE,
                    startDateTime,
                    endDateTime);

            if (totalRevenue == null) {
                totalRevenue = 0.0;
            }

            // Tính tổng chi phí
            Double totalExpense = financialTransactionRepo.sumAmountByTypeAndDateRange(
                    TransactionType.EXPENSE,
                    startDateTime,
                    endDateTime);

            if (totalExpense == null) {
                totalExpense = 0.0;
            }

            // Tính lợi nhuận (cho thuế TNDN)
            Double profit = totalRevenue - totalExpense;

            Map<String, Object> result = new HashMap<>();
            result.put("periodStart", periodStart);
            result.put("periodEnd", periodEnd);
            result.put("totalRevenue", totalRevenue);
            result.put("totalExpense", totalExpense);
            result.put("profit", profit);
            result.put("vatTaxableRevenue", totalRevenue); // Doanh thu chịu thuế VAT
            result.put("corporateTaxableRevenue", profit); // Lợi nhuận chịu thuế TNDN
            result.put("estimatedVAT", totalRevenue * 0.10); // VAT 10%
            result.put("estimatedCorporateTax", profit > 0 ? profit * 0.20 : 0.0); // Thuế TNDN 20%

            return ApiResponse.success("Tính toán doanh thu chịu thuế thành công", result);
        } catch (Exception e) {
            return ApiResponse.error("Lỗi khi tính toán: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ApiResponse autoCreateTaxReport(String periodStart, String periodEnd, TaxType taxType, String createdBy) {
        try {
            LocalDate startDate = LocalDate.parse(periodStart);
            LocalDate endDate = LocalDate.parse(periodEnd);

            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

            Double taxableRevenue;
            Double taxRate;

            if (taxType == TaxType.VAT) {
                // VAT: Tính trên doanh thu
                taxableRevenue = financialTransactionRepo.sumAmountByTypeAndDateRange(
                        TransactionType.REVENUE,
                        startDateTime,
                        endDateTime);
                taxRate = 10.0; // VAT 10%
            } else {
                // Corporate Tax: Tính trên lợi nhuận
                Double revenue = financialTransactionRepo.sumAmountByTypeAndDateRange(
                        TransactionType.REVENUE,
                        startDateTime,
                        endDateTime);
                Double expense = financialTransactionRepo.sumAmountByTypeAndDateRange(
                        TransactionType.EXPENSE,
                        startDateTime,
                        endDateTime);

                if (revenue == null)
                    revenue = 0.0;
                if (expense == null)
                    expense = 0.0;

                taxableRevenue = revenue - expense; // Lợi nhuận
                taxRate = 20.0; // Thuế TNDN 20%
            }

            if (taxableRevenue == null || taxableRevenue <= 0) {
                return ApiResponse.error("Không có doanh thu/lợi nhuận trong kỳ này");
            }

            // Tính số thuế
            Double taxAmount = taxableRevenue * (taxRate / 100);

            // Tạo report code
            String reportCode = taxType.name() + "-" +
                    startDate.format(java.time.format.DateTimeFormatter.ofPattern("MMyyyy"));

            // Kiểm tra xem đã có báo cáo này chưa
            if (taxReportRepository.findByReportCode(reportCode).isPresent()) {
                return ApiResponse.error("Báo cáo thuế cho kỳ này đã tồn tại: " + reportCode);
            }

            TaxReport report = TaxReport.builder()
                    .reportCode(reportCode)
                    .taxType(taxType)
                    .periodStart(startDate)
                    .periodEnd(endDate)
                    .taxableRevenue(taxableRevenue)
                    .taxRate(taxRate)
                    .taxAmount(taxAmount)
                    .paidAmount(0.0)
                    .remainingTax(taxAmount)
                    .status(TaxStatus.DRAFT)
                    .createdBy(createdBy)
                    .build();

            TaxReport saved = taxReportRepository.save(report);
            return ApiResponse.success("Tạo báo cáo thuế tự động thành công", toResponse(saved));
        } catch (Exception e) {
            return ApiResponse.error("Lỗi khi tạo báo cáo tự động: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ApiResponse recalculateTaxReport(Long id) {
        try {
            // Tìm báo cáo
            TaxReport report = taxReportRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy báo cáo thuế"));

            // Chỉ cho phép cập nhật báo cáo DRAFT
            if (report.getStatus() != TaxStatus.DRAFT) {
                return ApiResponse.error("Chỉ có thể cập nhật báo cáo ở trạng thái Nháp");
            }

            // Tính lại doanh thu chịu thuế
            LocalDateTime startDateTime = report.getPeriodStart().atStartOfDay();
            LocalDateTime endDateTime = report.getPeriodEnd().atTime(23, 59, 59);

            Double newTaxableRevenue;

            if (report.getTaxType() == TaxType.VAT) {
                // VAT: Tính trên doanh thu
                newTaxableRevenue = financialTransactionRepo.sumAmountByTypeAndDateRange(
                        TransactionType.REVENUE,
                        startDateTime,
                        endDateTime);
            } else {
                // Corporate Tax: Tính trên lợi nhuận
                Double revenue = financialTransactionRepo.sumAmountByTypeAndDateRange(
                        TransactionType.REVENUE,
                        startDateTime,
                        endDateTime);
                Double expense = financialTransactionRepo.sumAmountByTypeAndDateRange(
                        TransactionType.EXPENSE,
                        startDateTime,
                        endDateTime);

                if (revenue == null)
                    revenue = 0.0;
                if (expense == null)
                    expense = 0.0;

                newTaxableRevenue = revenue - expense; // Lợi nhuận
            }

            if (newTaxableRevenue == null) {
                newTaxableRevenue = 0.0;
            }

            // Cập nhật doanh thu chịu thuế
            report.setTaxableRevenue(newTaxableRevenue);

            // Tính lại số thuế
            Double newTaxAmount = newTaxableRevenue * (report.getTaxRate() / 100);
            report.setTaxAmount(newTaxAmount);

            // Cập nhật số thuế còn nợ (trừ đi số đã nộp)
            report.setRemainingTax(newTaxAmount - report.getPaidAmount());

            TaxReport updated = taxReportRepository.save(report);
            return ApiResponse.success("Cập nhật dữ liệu báo cáo thuế thành công", toResponse(updated));
        } catch (Exception e) {
            return ApiResponse.error("Lỗi khi cập nhật báo cáo: " + e.getMessage());
        }
    }
}
