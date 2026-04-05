package com.doan.WEB_TMDT.module.accounting.scheduler;

import com.doan.WEB_TMDT.module.accounting.entity.AccountingPeriod;
import com.doan.WEB_TMDT.module.accounting.entity.PeriodStatus;
import com.doan.WEB_TMDT.module.accounting.entity.TaxReport;
import com.doan.WEB_TMDT.module.accounting.entity.TaxStatus;
import com.doan.WEB_TMDT.module.accounting.entity.TaxType;
import com.doan.WEB_TMDT.module.accounting.repository.AccountingPeriodRepository;
import com.doan.WEB_TMDT.module.accounting.repository.TaxReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class AccountingPeriodScheduler {

    private final AccountingPeriodRepository periodRepository;
    private final TaxReportRepository taxReportRepository;

    /**
     * Tự động tạo kỳ kế toán cho tháng hiện tại nếu chưa tồn tại
     * Chạy vào 00:01 ngày đầu tiên của mỗi tháng
     */
    @Scheduled(cron = "0 1 0 1 * ?")
    public void autoCreateMonthlyPeriod() {
        try {
            LocalDate now = LocalDate.now();
            YearMonth currentMonth = YearMonth.from(now);
            
            LocalDate startDate = currentMonth.atDay(1);
            LocalDate endDate = currentMonth.atEndOfMonth();
            
            // Kiểm tra xem kỳ đã tồn tại chưa
            if (periodRepository.existsByStartDateAndEndDate(startDate, endDate)) {
                log.info("Kỳ kế toán tháng {}/{} đã tồn tại", currentMonth.getMonthValue(), currentMonth.getYear());
            } else {
                // Tạo tên kỳ
                String periodName = String.format("Tháng %02d/%d", 
                    currentMonth.getMonthValue(), 
                    currentMonth.getYear());
                
                // Tạo kỳ mới
                AccountingPeriod period = AccountingPeriod.builder()
                    .name(periodName)
                    .startDate(startDate)
                    .endDate(endDate)
                    .status(PeriodStatus.OPEN)
                    .build();
                
                periodRepository.save(period);
                log.info("Đã tự động tạo kỳ kế toán: {}", periodName);
            }
            
            // Tự động tạo báo cáo thuế cho tháng hiện tại
            autoCreateMonthlyTaxReports(startDate, endDate, currentMonth);
            
        } catch (Exception e) {
            log.error("Lỗi khi tự động tạo kỳ kế toán: {}", e.getMessage(), e);
        }
    }

    /**
     * Tự động tạo báo cáo thuế VAT và TNDN cho tháng
     */
    private void autoCreateMonthlyTaxReports(LocalDate startDate, LocalDate endDate, YearMonth month) {
        try {
            String monthYear = String.format("%02d/%d", month.getMonthValue(), month.getYear());
            
            // Tạo báo cáo thuế VAT
            String vatCode = String.format("VAT-%02d%d", month.getMonthValue(), month.getYear());
            if (!taxReportRepository.existsByReportCode(vatCode)) {
                TaxReport vatReport = TaxReport.builder()
                    .reportCode(vatCode)
                    .taxType(TaxType.VAT)
                    .periodStart(startDate)
                    .periodEnd(endDate)
                    .taxableRevenue(0.0)
                    .taxRate(10.0)
                    .taxAmount(0.0)
                    .paidAmount(0.0)
                    .remainingTax(0.0)
                    .status(TaxStatus.DRAFT)
                    .createdBy("SYSTEM")
                    .build();
                
                taxReportRepository.save(vatReport);
                log.info("Đã tự động tạo báo cáo thuế VAT tháng {}", monthYear);
            }
            
            // Tạo báo cáo thuế TNDN
            String corpCode = String.format("TNDN-%02d%d", month.getMonthValue(), month.getYear());
            if (!taxReportRepository.existsByReportCode(corpCode)) {
                TaxReport corpReport = TaxReport.builder()
                    .reportCode(corpCode)
                    .taxType(TaxType.CORPORATE_TAX)
                    .periodStart(startDate)
                    .periodEnd(endDate)
                    .taxableRevenue(0.0)
                    .taxRate(20.0)
                    .taxAmount(0.0)
                    .paidAmount(0.0)
                    .remainingTax(0.0)
                    .status(TaxStatus.DRAFT)
                    .createdBy("SYSTEM")
                    .build();
                
                taxReportRepository.save(corpReport);
                log.info("Đã tự động tạo báo cáo thuế TNDN tháng {}", monthYear);
            }
            
        } catch (Exception e) {
            log.error("Lỗi khi tự động tạo báo cáo thuế: {}", e.getMessage(), e);
        }
    }

    /**
     * Tạo kỳ cho tháng hiện tại khi khởi động ứng dụng (nếu chưa có)
     */
    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE)
    public void createCurrentMonthPeriodOnStartup() {
        try {
            LocalDate now = LocalDate.now();
            YearMonth currentMonth = YearMonth.from(now);
            
            LocalDate startDate = currentMonth.atDay(1);
            LocalDate endDate = currentMonth.atEndOfMonth();
            
            // Kiểm tra xem kỳ hiện tại đã tồn tại chưa
            if (!periodRepository.existsByStartDateAndEndDate(startDate, endDate)) {
                String periodName = String.format("Tháng %02d/%d", 
                    currentMonth.getMonthValue(), 
                    currentMonth.getYear());
                
                AccountingPeriod period = AccountingPeriod.builder()
                    .name(periodName)
                    .startDate(startDate)
                    .endDate(endDate)
                    .status(PeriodStatus.OPEN)
                    .build();
                
                periodRepository.save(period);
                log.info("Đã tạo kỳ kế toán cho tháng hiện tại khi khởi động: {}", periodName);
            }
            
            // Tạo kỳ cho tháng trước nếu chưa có (để có dữ liệu lịch sử)
            YearMonth lastMonth = currentMonth.minusMonths(1);
            LocalDate lastMonthStart = lastMonth.atDay(1);
            LocalDate lastMonthEnd = lastMonth.atEndOfMonth();
            
            if (!periodRepository.existsByStartDateAndEndDate(lastMonthStart, lastMonthEnd)) {
                String lastMonthName = String.format("Tháng %02d/%d", 
                    lastMonth.getMonthValue(), 
                    lastMonth.getYear());
                
                AccountingPeriod lastPeriod = AccountingPeriod.builder()
                    .name(lastMonthName)
                    .startDate(lastMonthStart)
                    .endDate(lastMonthEnd)
                    .status(PeriodStatus.OPEN)
                    .build();
                
                periodRepository.save(lastPeriod);
                log.info("Đã tạo kỳ kế toán cho tháng trước: {}", lastMonthName);
            }
            
            // Tạo báo cáo thuế cho tháng hiện tại
            autoCreateMonthlyTaxReports(startDate, endDate, currentMonth);
            
            // Tạo báo cáo thuế cho tháng trước
            autoCreateMonthlyTaxReports(lastMonthStart, lastMonthEnd, lastMonth);
            
        } catch (Exception e) {
            log.error("Lỗi khi tạo kỳ kế toán ban đầu: {}", e.getMessage(), e);
        }
    }
}
