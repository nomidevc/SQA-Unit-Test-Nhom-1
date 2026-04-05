package com.doan.WEB_TMDT.module.accounting.service.impl;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.common.util.SecurityUtils;
import com.doan.WEB_TMDT.dto.DashboardStatsDTO;
import com.doan.WEB_TMDT.dto.OrderDTO;
import com.doan.WEB_TMDT.module.accounting.dto.ReconciliationRequest;
import com.doan.WEB_TMDT.module.accounting.entity.*;
import com.doan.WEB_TMDT.module.accounting.repository.*;
import com.doan.WEB_TMDT.module.accounting.service.AccountingService;
import com.doan.WEB_TMDT.module.order.entity.Order;
import com.doan.WEB_TMDT.module.order.entity.OrderStatus;
import com.doan.WEB_TMDT.module.order.repository.OrderRepository;
import com.doan.WEB_TMDT.module.product.repository.ProductRepository;
import com.doan.WEB_TMDT.module.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AccountingServiceImpl implements AccountingService {

    private final PaymentReconciliationRepository reconciliationRepo;
    private final AccountingPeriodRepository periodRepo;
    private final OrderRepository orderRepo;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final FinancialTransactionRepository financialTransactionRepo;

    @Override
    public ApiResponse getStats() {
        long pendingCount = reconciliationRepo.countByStatus(ReconciliationStatus.MISMATCHED);
        long completedCount = reconciliationRepo.countByStatus(ReconciliationStatus.MATCHED);
        Double discrepancyAmount = reconciliationRepo.sumDiscrepancyByStatus(ReconciliationStatus.MISMATCHED);

        // Get real revenue from orders (last 30 days)
        LocalDateTime startDate = LocalDateTime.now().minusDays(30);
        LocalDateTime endDate = LocalDateTime.now();
        Double totalRevenue = orderRepo.sumTotalByDateRange(startDate, endDate);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRevenue", totalRevenue != null ? totalRevenue : 0);
        stats.put("pendingReconciliation", pendingCount);
        stats.put("completedReconciliation", completedCount);
        stats.put("discrepancies", pendingCount);
        stats.put("discrepancyAmount", discrepancyAmount != null ? discrepancyAmount : 0);

        return ApiResponse.success("Thống kê kế toán", stats);
    }

    @Override
    public ApiResponse getDashboardStats() {
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime endOfToday = LocalDate.now().atTime(LocalTime.MAX);

        LocalDateTime startOfYesterday = LocalDate.now().minusDays(1).atStartOfDay();
        LocalDateTime endOfYesterday = LocalDate.now().minusDays(1).atTime(LocalTime.MAX);

        // Lấy doanh thu từ financial_transactions (REVENUE)
        Double todayRevenue = financialTransactionRepo.sumAmountByTypeAndDateRange(
                TransactionType.REVENUE, startOfToday, endOfToday);
        if (todayRevenue == null)
            todayRevenue = 0.0;

        Double yesterdayRevenue = financialTransactionRepo.sumAmountByTypeAndDateRange(
                TransactionType.REVENUE, startOfYesterday, endOfYesterday);
        if (yesterdayRevenue == null)
            yesterdayRevenue = 0.0;

        // Lấy chi phí từ financial_transactions (EXPENSE)
        Double todayExpense = financialTransactionRepo.sumAmountByTypeAndDateRange(
                TransactionType.EXPENSE, startOfToday, endOfToday);
        if (todayExpense == null)
            todayExpense = 0.0;

        Double yesterdayExpense = financialTransactionRepo.sumAmountByTypeAndDateRange(
                TransactionType.EXPENSE, startOfYesterday, endOfYesterday);
        if (yesterdayExpense == null)
            yesterdayExpense = 0.0;

        // Tính lợi nhuận
        Double todayProfit = todayRevenue - todayExpense;
        Double yesterdayProfit = yesterdayRevenue - yesterdayExpense;

        // Tính tỷ suất lợi nhuận
        Double profitMargin = todayRevenue > 0 ? (todayProfit / todayRevenue) * 100 : 0.0;

        // Đếm số đơn hàng hôm nay
        Long todayOrders = orderRepo.findAll().stream()
                .filter(order -> order.getCreatedAt().isAfter(startOfToday) &&
                        order.getCreatedAt().isBefore(endOfToday))
                .count();

        Long yesterdayOrders = orderRepo.findAll().stream()
                .filter(order -> order.getCreatedAt().isAfter(startOfYesterday) &&
                        order.getCreatedAt().isBefore(endOfYesterday))
                .count();

        // Total stats
        Long totalProducts = productRepository.count();
        Long totalCustomers = userRepository.findAll().stream()
                .filter(user -> user.getRole().name().equals("CUSTOMER"))
                .count();

        // Calculate percentage changes
        Double revenueChange = calculatePercentageChange(todayRevenue, yesterdayRevenue);
        Double ordersChange = calculatePercentageChange(todayOrders, yesterdayOrders);
        Double profitChange = calculatePercentageChange(todayProfit, yesterdayProfit);

        DashboardStatsDTO stats = DashboardStatsDTO.builder()
                .totalRevenue(todayRevenue)
                .totalOrders(todayOrders)
                .totalProfit(todayProfit)
                .profitMargin(profitMargin)
                .totalProducts(totalProducts)
                .totalCustomers(totalCustomers)
                .revenueChangePercent(revenueChange)
                .ordersChangePercent(ordersChange)
                .profitChangePercent(profitChange)
                .build();

        return ApiResponse.success("Dashboard stats", stats);
    }

    @Override
    public ApiResponse getRecentOrders(int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<OrderDTO> orders = orderRepo.findAll(pageRequest).getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        return ApiResponse.success("Recent orders", orders);
    }

    private OrderDTO convertToDTO(Order order) {
        String customerEmail = "N/A";
        if (order.getCustomer() != null && order.getCustomer().getUser() != null) {
            customerEmail = order.getCustomer().getUser().getEmail();
        }

        return OrderDTO.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .totalAmount(order.getTotal())
                .status(order.getStatus().name())
                .createdAt(order.getCreatedAt())
                .customerName(order.getCustomer() != null ? order.getCustomer().getFullName() : "N/A")
                .customerEmail(customerEmail)
                .build();
    }

    private Double calculatePercentageChange(Number current, Number previous) {
        if (previous == null || previous.doubleValue() == 0) {
            return 0.0;
        }
        double change = ((current.doubleValue() - previous.doubleValue()) / previous.doubleValue()) * 100;
        return Math.round(change * 10.0) / 10.0;
    }

    @Override
    @Transactional
    public ApiResponse getPaymentReconciliation(ReconciliationRequest request) {
        LocalDateTime startDateTime = request.getStartDate().atStartOfDay();
        LocalDateTime endDateTime = request.getEndDate().atTime(23, 59, 59);

        // Lấy TẤT CẢ đơn hàng trong khoảng thời gian (kể cả chưa thanh toán)
        List<Order> allOrders = orderRepo.findByCreatedAtBetween(startDateTime, endDateTime);

        // Lấy dữ liệu đối soát đã import từ cổng thanh toán
        List<PaymentReconciliation> existingReconciliations;
        if ("ALL".equals(request.getGateway())) {
            existingReconciliations = reconciliationRepo.findByTransactionDateBetween(startDateTime, endDateTime);
        } else {
            existingReconciliations = reconciliationRepo.findByGatewayAndTransactionDateBetween(
                    request.getGateway(), startDateTime, endDateTime);
        }

        // Tạo map để tra cứu nhanh
        Map<String, PaymentReconciliation> reconciliationMap = new HashMap<>();
        for (PaymentReconciliation rec : existingReconciliations) {
            reconciliationMap.put(rec.getOrderId(), rec);
        }

        // Tạo danh sách kết quả bao gồm TẤT CẢ đơn hàng
        List<Map<String, Object>> results = new ArrayList<>();

        for (Order order : allOrders) {
            Map<String, Object> item = new HashMap<>();

            item.put("orderId", order.getOrderCode());
            item.put("orderStatus", order.getStatus().name());
            item.put("paymentStatus", order.getPaymentStatus().name());
            item.put("systemAmount", BigDecimal.valueOf(order.getTotal()));
            item.put("transactionDate", order.getCreatedAt());

            // Kiểm tra xem có dữ liệu đối soát từ gateway không
            PaymentReconciliation reconciliation = reconciliationMap.get(order.getOrderCode());

            if (reconciliation != null) {
                // Có dữ liệu từ gateway
                item.put("transactionId", reconciliation.getTransactionId());
                item.put("gateway", reconciliation.getGateway());
                item.put("gatewayAmount", reconciliation.getGatewayAmount());
                item.put("discrepancy", reconciliation.getDiscrepancy());
                item.put("status", reconciliation.getStatus().name());

                // Cập nhật system amount nếu cần
                BigDecimal systemAmount = BigDecimal.valueOf(order.getTotal());
                if (!systemAmount.equals(reconciliation.getSystemAmount())) {
                    reconciliation.setSystemAmount(systemAmount);
                    reconciliation.setDiscrepancy(systemAmount.subtract(reconciliation.getGatewayAmount()).abs());

                    if (reconciliation.getDiscrepancy().compareTo(BigDecimal.ZERO) == 0) {
                        reconciliation.setStatus(ReconciliationStatus.MATCHED);
                    } else {
                        reconciliation.setStatus(ReconciliationStatus.MISMATCHED);
                    }
                    reconciliationRepo.save(reconciliation);

                    item.put("discrepancy", reconciliation.getDiscrepancy());
                    item.put("status", reconciliation.getStatus().name());
                }
            } else {
                // Chưa có dữ liệu từ gateway
                item.put("transactionId", "-");
                item.put("gateway", "-");
                item.put("gatewayAmount", BigDecimal.ZERO);
                item.put("discrepancy", BigDecimal.ZERO);

                // Xác định trạng thái dựa vào payment status
                if (order.getPaymentStatus().name().equals("PAID")) {
                    item.put("status", "MISSING_IN_GATEWAY"); // Đã thanh toán nhưng chưa có trong gateway
                } else {
                    item.put("status", "PENDING_PAYMENT"); // Chưa thanh toán
                }
            }

            results.add(item);
        }

        // Tính summary
        Map<String, Object> summary = calculateSummaryFromResults(results);

        Map<String, Object> result = new HashMap<>();
        result.put("data", results);
        result.put("summary", summary);

        return ApiResponse.success("Dữ liệu đối soát", result);
    }

    private Map<String, Object> calculateSummaryFromResults(List<Map<String, Object>> results) {
        Map<String, Object> summary = new HashMap<>();

        long total = results.size();
        long matched = results.stream().filter(r -> "MATCHED".equals(r.get("status"))).count();
        long mismatched = results.stream().filter(r -> "MISMATCHED".equals(r.get("status"))).count();
        long missing = results.stream().filter(r -> "MISSING_IN_SYSTEM".equals(r.get("status")) ||
                "MISSING_IN_GATEWAY".equals(r.get("status"))).count();
        long pending = results.stream().filter(r -> "PENDING_PAYMENT".equals(r.get("status"))).count();

        BigDecimal totalAmount = results.stream()
                .map(r -> (BigDecimal) r.get("systemAmount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal discrepancyAmount = results.stream()
                .map(r -> (BigDecimal) r.get("discrepancy"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        summary.put("total", total);
        summary.put("matched", matched);
        summary.put("mismatched", mismatched);
        summary.put("missing", missing);
        summary.put("pending", pending);
        summary.put("totalAmount", totalAmount);
        summary.put("discrepancyAmount", discrepancyAmount);

        return summary;
    }

    @Override
    @Transactional
    public ApiResponse importReconciliationFile(MultipartFile file, String gateway) {
        try {
            List<PaymentReconciliation> reconciliations = new ArrayList<>();

            BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()));
            String line;
            boolean isFirstLine = true;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // Skip header
                }

                String[] values = line.split(",");
                if (values.length < 4)
                    continue;

                String orderCode = values[0].trim();
                String transactionId = values[1].trim();
                BigDecimal gatewayAmount = new BigDecimal(values[2].trim());
                LocalDateTime transactionDate = LocalDateTime.parse(values[3].trim());

                // Query system amount from orders
                BigDecimal systemAmount = BigDecimal.ZERO;
                Optional<Order> orderOpt = orderRepo.findByOrderCode(orderCode);
                if (orderOpt.isPresent()) {
                    systemAmount = BigDecimal.valueOf(orderOpt.get().getTotal());
                }

                BigDecimal discrepancy = systemAmount.subtract(gatewayAmount).abs();

                ReconciliationStatus status;
                if (systemAmount.compareTo(BigDecimal.ZERO) == 0) {
                    status = ReconciliationStatus.MISSING_IN_SYSTEM;
                } else if (discrepancy.compareTo(BigDecimal.ZERO) == 0) {
                    status = ReconciliationStatus.MATCHED;
                } else {
                    status = ReconciliationStatus.MISMATCHED;
                }

                PaymentReconciliation reconciliation = PaymentReconciliation.builder()
                        .orderId(orderCode)
                        .transactionId(transactionId)
                        .gateway(gateway)
                        .systemAmount(systemAmount)
                        .gatewayAmount(gatewayAmount)
                        .discrepancy(discrepancy)
                        .status(status)
                        .transactionDate(transactionDate)
                        .createdAt(LocalDateTime.now())
                        .build();

                reconciliations.add(reconciliation);
            }

            reconciliationRepo.saveAll(reconciliations);

            return ApiResponse.success("Import thành công " + reconciliations.size() + " giao dịch", reconciliations);
        } catch (Exception e) {
            return ApiResponse.error("Lỗi khi import file: " + e.getMessage());
        }
    }

    @Override
    public ApiResponse getShippingReconciliation(LocalDate startDate, LocalDate endDate) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        // Lấy tất cả đơn hàng trong khoảng thời gian
        List<Order> orders = orderRepo.findPaidOrdersBetween(startDateTime, endDateTime);

        BigDecimal totalShippingFeeCollected = BigDecimal.ZERO; // Phí vận chuyển thu từ khách
        BigDecimal totalShippingCostPaid = BigDecimal.ZERO; // Chi phí trả cho đối tác vận chuyển
        int totalOrders = orders.size();

        List<Map<String, Object>> shippingDetails = new ArrayList<>();

        for (Order order : orders) {
            BigDecimal shippingFeeCollected = BigDecimal.valueOf(order.getShippingFee());
            // Giả định chi phí thực tế trả cho đối tác = 80% phí thu từ khách
            BigDecimal actualShippingCost = shippingFeeCollected.multiply(BigDecimal.valueOf(0.8));

            totalShippingFeeCollected = totalShippingFeeCollected.add(shippingFeeCollected);
            totalShippingCostPaid = totalShippingCostPaid.add(actualShippingCost);

            Map<String, Object> detail = new HashMap<>();
            detail.put("orderId", order.getOrderCode());
            detail.put("shippingFeeCollected", shippingFeeCollected);
            detail.put("actualShippingCost", actualShippingCost);
            detail.put("profit", shippingFeeCollected.subtract(actualShippingCost));
            detail.put("orderDate", order.getCreatedAt().toLocalDate());
            detail.put("shippingAddress", order.getShippingAddress());

            shippingDetails.add(detail);
        }

        BigDecimal shippingProfit = totalShippingFeeCollected.subtract(totalShippingCostPaid);
        BigDecimal profitMargin = totalShippingFeeCollected.compareTo(BigDecimal.ZERO) > 0
                ? shippingProfit.divide(totalShippingFeeCollected, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        Map<String, Object> result = new HashMap<>();
        result.put("period", startDate + " - " + endDate);
        result.put("totalOrders", totalOrders);
        result.put("totalShippingFeeCollected", totalShippingFeeCollected);
        result.put("totalShippingCostPaid", totalShippingCostPaid);
        result.put("shippingProfit", shippingProfit);
        result.put("profitMargin", profitMargin);
        result.put("details", shippingDetails);

        return ApiResponse.success("Đối soát vận chuyển", result);
    }

    @Override
    public ApiResponse getFinancialReports(LocalDate startDate, LocalDate endDate, String viewMode) {
        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        List<Order> orders = orderRepo.findPaidOrdersBetween(startDateTime, endDateTime);
        List<Map<String, Object>> reports = new ArrayList<>();

        if ("ORDERS".equals(viewMode)) {
            // Chi tiết từng đơn hàng
            for (Order order : orders) {
                Map<String, Object> report = calculateOrderFinancials(order);
                reports.add(report);
            }
        } else {
            // Tổng hợp theo ngày/tháng
            Map<String, List<Order>> groupedOrders = groupOrdersByPeriod(orders, viewMode);
            for (Map.Entry<String, List<Order>> entry : groupedOrders.entrySet()) {
                Map<String, Object> report = calculatePeriodFinancials(entry.getKey(), entry.getValue());
                reports.add(report);
            }
        }

        return ApiResponse.success("Báo cáo tài chính", reports);
    }

    private Map<String, Object> calculateOrderFinancials(Order order) {
        Map<String, Object> report = new HashMap<>();

        double revenue = order.getTotal();
        double shippingCost = order.getShippingFee();

        double totalCosts = shippingCost;
        double grossProfit = revenue - totalCosts;

        double vat = grossProfit * 0.1; // VAT 10%
        double profitAfterVAT = grossProfit - vat;
        double corporateTax = profitAfterVAT * 0.2; // Thuế TNDN 20%
        double netProfit = profitAfterVAT - corporateTax;

        report.put("orderId", order.getOrderCode());
        report.put("date", order.getCreatedAt().toLocalDate().toString());
        report.put("revenue", Math.round(revenue));
        report.put("shippingCost", Math.round(shippingCost));
        report.put("totalCosts", Math.round(totalCosts));
        report.put("grossProfit", Math.round(grossProfit));
        report.put("vat", Math.round(vat));
        report.put("corporateTax", Math.round(corporateTax));
        report.put("netProfit", Math.round(netProfit));

        return report;
    }

    private Map<String, List<Order>> groupOrdersByPeriod(List<Order> orders, String viewMode) {
        Map<String, List<Order>> grouped = new LinkedHashMap<>();

        for (Order order : orders) {
            String key;
            if ("DAILY".equals(viewMode)) {
                key = order.getCreatedAt().toLocalDate().toString();
            } else {
                key = order.getCreatedAt().getYear() + "-" +
                        String.format("%02d", order.getCreatedAt().getMonthValue());
            }

            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(order);
        }

        return grouped;
    }

    private Map<String, Object> calculatePeriodFinancials(String period, List<Order> orders) {
        Map<String, Object> report = new HashMap<>();

        double totalRevenue = 0;
        double totalShippingCost = 0;

        for (Order order : orders) {
            double revenue = order.getTotal();
            totalRevenue += revenue;
            totalShippingCost += order.getShippingFee();
        }

        double totalCosts = totalShippingCost;
        double grossProfit = totalRevenue - totalCosts;

        double totalVat = grossProfit * 0.1;
        double profitAfterVAT = grossProfit - totalVat;
        double corporateTax = profitAfterVAT * 0.2;
        double netProfit = profitAfterVAT - corporateTax;

        report.put("period", period);
        report.put("orderCount", orders.size());
        report.put("revenue", Math.round(totalRevenue));
        report.put("shippingCost", Math.round(totalShippingCost));
        report.put("totalCosts", Math.round(totalCosts));
        report.put("grossProfit", Math.round(grossProfit));
        report.put("vat", Math.round(totalVat));
        report.put("corporateTax", Math.round(corporateTax));
        report.put("netProfit", Math.round(netProfit));

        return report;
    }

    @Override
    public ApiResponse exportReports(LocalDate startDate, LocalDate endDate) {
        try {
            LocalDateTime startDateTime = startDate.atStartOfDay();
            LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

            List<Order> orders = orderRepo.findPaidOrdersBetween(startDateTime, endDateTime);
            List<Map<String, Object>> reports = new ArrayList<>();

            for (Order order : orders) {
                Map<String, Object> report = calculateOrderFinancials(order);
                reports.add(report);
            }

            // TODO: Implement Excel export functionality
            return ApiResponse.error("Chức năng xuất Excel đang được phát triển");

            // byte[] excelData = excelExportService.exportFinancialReport(reports);
            // String base64Excel = Base64.getEncoder().encodeToString(excelData);
            //
            // Map<String, Object> result = new HashMap<>();
            // result.put("fileName", "BaoCaoTaiChinh_" + startDate + "_" + endDate +
            // ".xlsx");
            // result.put("data", base64Excel);
            //
            // return ApiResponse.success("Xuất báo cáo thành công!", result);
        } catch (Exception e) {
            return ApiResponse.error("Lỗi khi xuất báo cáo: " + e.getMessage());
        }
    }

    @Override
    public ApiResponse getAllPeriods() {
        List<AccountingPeriod> periods = periodRepo.findAllByOrderByStartDateDesc();
        return ApiResponse.success("Danh sách kỳ báo cáo", periods);
    }

    @Override
    @Transactional
    public ApiResponse closePeriod(Long id) {
        AccountingPeriod period = periodRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kỳ báo cáo"));

        if (period.getStatus() == PeriodStatus.CLOSED) {
            return ApiResponse.error("Kỳ này đã được chốt!");
        }

        // Tính toán lại sai số trước khi chốt
        LocalDateTime startDateTime = period.getStartDate().atStartOfDay();
        LocalDateTime endDateTime = period.getEndDate().atTime(23, 59, 59);

        // Tính doanh thu thực tế từ đơn hàng
        Double actualRevenue = orderRepo.sumTotalByDateRange(startDateTime, endDateTime);
        if (actualRevenue == null)
            actualRevenue = 0.0;

        // Tính tổng sai lệch từ đối soát thanh toán
        Double totalDiscrepancy = reconciliationRepo.sumDiscrepancyByDateRange(startDateTime, endDateTime);
        if (totalDiscrepancy == null)
            totalDiscrepancy = 0.0;

        // Cập nhật thông tin kỳ
        period.setTotalRevenue(actualRevenue);

        // Tính tỷ lệ sai số
        double discrepancyRate = actualRevenue > 0 ? (totalDiscrepancy / actualRevenue) * 100 : 0;
        period.setDiscrepancyRate(discrepancyRate);

        // Kiểm tra sai số > 15%
        if (discrepancyRate > 15) {
            periodRepo.save(period); // Lưu thông tin đã cập nhật
            return ApiResponse.error(String.format(
                    "Sai số %.2f%% vượt quá 15%%. Vui lòng kiểm tra và xử lý các sai lệch trước khi chốt kỳ. " +
                            "Tổng sai lệch: %,.0f ₫ / Tổng doanh thu: %,.0f ₫",
                    discrepancyRate, totalDiscrepancy, actualRevenue));
        }

        String currentUser = SecurityUtils.getCurrentUserEmail();

        period.setStatus(PeriodStatus.CLOSED);
        period.setClosedBy(currentUser != null ? currentUser : "System");
        period.setClosedAt(LocalDateTime.now());

        periodRepo.save(period);

        return ApiResponse.success("Đã chốt kỳ báo cáo thành công!", period);
    }

    @Override
    @Transactional
    public ApiResponse reopenPeriod(Long id) {
        AccountingPeriod period = periodRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy kỳ báo cáo"));

        if (period.getStatus() == PeriodStatus.OPEN) {
            return ApiResponse.error("Kỳ này đang mở!");
        }

        // Check if user is ADMIN
        if (!SecurityUtils.isAdmin()) {
            return ApiResponse.error("Chỉ Admin mới có quyền mở khóa kỳ báo cáo!");
        }

        period.setStatus(PeriodStatus.OPEN);
        period.setClosedBy(null);
        period.setClosedAt(null);

        periodRepo.save(period);

        return ApiResponse.success("Đã mở khóa kỳ báo cáo!", period);
    }

    @Override
    public ApiResponse exportShippingReconciliation(LocalDate startDate, LocalDate endDate) {
        try {
            // Lấy dữ liệu đối soát vận chuyển
            ApiResponse reconciliationResponse = getShippingReconciliation(startDate, endDate);
            if (!reconciliationResponse.isSuccess()) {
                return reconciliationResponse;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) reconciliationResponse.getData();
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> details = (List<Map<String, Object>>) data.get("details");

            // TODO: Implement Excel export functionality
            return ApiResponse.error("Chức năng xuất Excel đang được phát triển");

            // byte[] excelData = excelExportService.exportShippingReconciliation(details);
            // String base64Excel = Base64.getEncoder().encodeToString(excelData);
            //
            // Map<String, Object> result = new HashMap<>();
            // result.put("fileName", "DoiSoatVanChuyen_" + startDate + "_" + endDate +
            // ".xlsx");
            // result.put("data", base64Excel);
            //
            // return ApiResponse.success("Xuất báo cáo đối soát vận chuyển thành công!",
            // result);
        } catch (Exception e) {
            return ApiResponse.error("Lỗi khi xuất báo cáo: " + e.getMessage());
        }
    }

}
