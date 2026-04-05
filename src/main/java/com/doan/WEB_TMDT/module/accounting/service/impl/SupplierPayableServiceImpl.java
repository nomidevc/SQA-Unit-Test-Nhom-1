package com.doan.WEB_TMDT.module.accounting.service.impl;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.common.util.SecurityUtils;
import com.doan.WEB_TMDT.module.accounting.dto.CreatePaymentRequest;
import com.doan.WEB_TMDT.module.accounting.dto.SupplierPayableResponse;
import com.doan.WEB_TMDT.module.accounting.entity.*;
import com.doan.WEB_TMDT.module.accounting.repository.FinancialTransactionRepository;
import com.doan.WEB_TMDT.module.accounting.repository.SupplierPayableRepository;
import com.doan.WEB_TMDT.module.accounting.repository.SupplierPaymentRepository;
import com.doan.WEB_TMDT.module.accounting.service.SupplierPayableService;
import com.doan.WEB_TMDT.module.inventory.entity.PurchaseOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierPayableServiceImpl implements SupplierPayableService {

    private final SupplierPayableRepository payableRepository;
    private final SupplierPaymentRepository paymentRepository;
    private final FinancialTransactionRepository financialTransactionRepository;

    @Override
    @Transactional
    public ApiResponse createPayableFromPurchaseOrder(PurchaseOrder purchaseOrder) {
        try {
            // Kiểm tra đã tồn tại công nợ chưa
            Optional<SupplierPayable> existing = payableRepository.findByPurchaseOrderId(purchaseOrder.getId());
            if (existing.isPresent()) {
                return ApiResponse.error("Công nợ cho đơn nhập hàng này đã tồn tại");
            }

            // Tính tổng tiền
            BigDecimal totalAmount = purchaseOrder.getItems().stream()
                    .map(item -> BigDecimal.valueOf(item.getQuantity())
                            .multiply(BigDecimal.valueOf(item.getUnitCost())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Lấy số ngày nợ từ supplier
            Integer paymentTermDays = purchaseOrder.getSupplier().getPaymentTermDays();
            if (paymentTermDays == null) {
                paymentTermDays = 30; // Mặc định 30 ngày
            }

            // Tính ngày hạn thanh toán
            LocalDate invoiceDate = purchaseOrder.getReceivedDate() != null 
                    ? purchaseOrder.getReceivedDate().toLocalDate()
                    : LocalDate.now();
            LocalDate dueDate = invoiceDate.plusDays(paymentTermDays);

            // Tạo mã công nợ
            String payableCode = generatePayableCode();

            // Tạo công nợ
            SupplierPayable payable = SupplierPayable.builder()
                    .payableCode(payableCode)
                    .supplier(purchaseOrder.getSupplier())
                    .purchaseOrder(purchaseOrder)
                    .totalAmount(totalAmount)
                    .paidAmount(BigDecimal.ZERO)
                    .remainingAmount(totalAmount)
                    .status(PayableStatus.UNPAID)
                    .invoiceDate(invoiceDate)
                    .dueDate(dueDate)
                    .paymentTermDays(paymentTermDays)
                    .createdBy(SecurityUtils.getCurrentUserEmail())
                    .build();

            payableRepository.save(payable);

            log.info("Created payable {} for PO {} - Amount: {}, Due date: {}", 
                    payableCode, purchaseOrder.getPoCode(), totalAmount, dueDate);

            return ApiResponse.success("Đã tạo công nợ", toResponse(payable));
        } catch (Exception e) {
            log.error("Error creating payable from PO: {}", e.getMessage(), e);
            return ApiResponse.error("Lỗi khi tạo công nợ: " + e.getMessage());
        }
    }

    @Override
    public ApiResponse getAllPayables() {
        try {
            List<SupplierPayable> payables = payableRepository.findAll();
            List<SupplierPayableResponse> responses = payables.stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
            return ApiResponse.success("Danh sách công nợ", responses);
        } catch (Exception e) {
            log.error("Error getting all payables: {}", e.getMessage(), e);
            return ApiResponse.error("Lỗi khi lấy danh sách công nợ");
        }
    }

    @Override
    public ApiResponse getPayableById(Long id) {
        try {
            SupplierPayable payable = payableRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy công nợ"));
            return ApiResponse.success("Thông tin công nợ", toResponse(payable));
        } catch (Exception e) {
            log.error("Error getting payable by id: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @Override
    public ApiResponse getPayablesBySupplier(Long supplierId) {
        try {
            List<SupplierPayable> payables = payableRepository.findBySupplierId(supplierId);
            List<SupplierPayableResponse> responses = payables.stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
            
            // Tính tổng công nợ
            BigDecimal totalOutstanding = payableRepository.getTotalPayableBySupplier(supplierId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("payables", responses);
            result.put("totalOutstanding", totalOutstanding != null ? totalOutstanding : BigDecimal.ZERO);
            
            return ApiResponse.success("Công nợ theo nhà cung cấp", result);
        } catch (Exception e) {
            log.error("Error getting payables by supplier: {}", e.getMessage(), e);
            return ApiResponse.error("Lỗi khi lấy công nợ theo nhà cung cấp");
        }
    }

    @Override
    public ApiResponse getOverduePayables() {
        try {
            List<SupplierPayable> payables = payableRepository.findOverduePayables(LocalDate.now());
            List<SupplierPayableResponse> responses = payables.stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
            return ApiResponse.success("Công nợ quá hạn", responses);
        } catch (Exception e) {
            log.error("Error getting overdue payables: {}", e.getMessage(), e);
            return ApiResponse.error("Lỗi khi lấy công nợ quá hạn");
        }
    }

    @Override
    public ApiResponse getUpcomingPayables(Integer days) {
        try {
            LocalDate startDate = LocalDate.now();
            LocalDate endDate = startDate.plusDays(days != null ? days : 7);
            
            List<SupplierPayable> payables = payableRepository.findUpcomingPayables(startDate, endDate);
            List<SupplierPayableResponse> responses = payables.stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
            return ApiResponse.success("Công nợ sắp đến hạn", responses);
        } catch (Exception e) {
            log.error("Error getting upcoming payables: {}", e.getMessage(), e);
            return ApiResponse.error("Lỗi khi lấy công nợ sắp đến hạn");
        }
    }

    @Override
    @Transactional
    public ApiResponse makePayment(CreatePaymentRequest request) {
        try {
            // Lấy công nợ
            SupplierPayable payable = payableRepository.findById(request.getPayableId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy công nợ"));

            // Kiểm tra số tiền
            if (request.getAmount().compareTo(payable.getRemainingAmount()) > 0) {
                return ApiResponse.error("Số tiền thanh toán vượt quá số tiền còn nợ");
            }

            // Tạo mã thanh toán
            String paymentCode = generatePaymentCode();

            // Tạo payment record
            SupplierPayment payment = SupplierPayment.builder()
                    .paymentCode(paymentCode)
                    .payable(payable)
                    .amount(request.getAmount())
                    .paymentDate(request.getPaymentDate())
                    .paymentMethod(request.getPaymentMethod())
                    .referenceNumber(request.getReferenceNumber())
                    .note(request.getNote())
                    .createdBy(SecurityUtils.getCurrentUserEmail())
                    .build();

            paymentRepository.save(payment);

            // Tạo Financial Transaction để tracking
            String transactionCode = "TXN-PAY-" + System.currentTimeMillis();
            FinancialTransaction transaction = FinancialTransaction.builder()
                    .transactionCode(transactionCode)
                    .type(TransactionType.EXPENSE) // Thanh toán NCC là chi phí
                    .category(TransactionCategory.SUPPLIER_PAYMENT)
                    .amount(request.getAmount().doubleValue())
                    .transactionDate(request.getPaymentDate().atStartOfDay())
                    .description("Thanh toán công nợ " + payable.getPayableCode() + 
                               " cho NCC " + payable.getSupplier().getName())
                    .supplierId(payable.getSupplier().getId())
                    .createdBy(SecurityUtils.getCurrentUserEmail())
                    .build();

            financialTransactionRepository.save(transaction);

            // Cập nhật công nợ
            payable.setPaidAmount(payable.getPaidAmount().add(request.getAmount()));
            payable.setRemainingAmount(payable.getRemainingAmount().subtract(request.getAmount()));
            payable.updateStatus();
            payableRepository.save(payable);

            log.info("Payment {} created for payable {} - Amount: {}, Transaction: {}", 
                    paymentCode, payable.getPayableCode(), request.getAmount(), transactionCode);

            return ApiResponse.success("Thanh toán thành công", toResponse(payable));
        } catch (Exception e) {
            log.error("Error making payment: {}", e.getMessage(), e);
            return ApiResponse.error("Lỗi khi thanh toán: " + e.getMessage());
        }
    }

    @Override
    public ApiResponse getPaymentHistory(Long payableId) {
        try {
            List<SupplierPayment> payments = paymentRepository.findByPayableId(payableId);
            return ApiResponse.success("Lịch sử thanh toán", payments);
        } catch (Exception e) {
            log.error("Error getting payment history: {}", e.getMessage(), e);
            return ApiResponse.error("Lỗi khi lấy lịch sử thanh toán");
        }
    }

    @Override
    public ApiResponse getPayableStats() {
        try {
            BigDecimal totalOutstanding = payableRepository.getTotalOutstandingPayables();
            List<SupplierPayable> overdueList = payableRepository.findOverduePayables(LocalDate.now());
            List<SupplierPayable> upcomingList = payableRepository.findUpcomingPayables(
                    LocalDate.now(), LocalDate.now().plusDays(7));

            Map<String, Object> stats = new HashMap<>();
            stats.put("totalOutstanding", totalOutstanding != null ? totalOutstanding : BigDecimal.ZERO);
            stats.put("overdueCount", overdueList.size());
            stats.put("upcomingCount", upcomingList.size());
            stats.put("overdueAmount", overdueList.stream()
                    .map(SupplierPayable::getRemainingAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));

            return ApiResponse.success("Thống kê công nợ", stats);
        } catch (Exception e) {
            log.error("Error getting payable stats: {}", e.getMessage(), e);
            return ApiResponse.error("Lỗi khi lấy thống kê công nợ");
        }
    }

    @Override
    public ApiResponse getPayableReport(LocalDate startDate, LocalDate endDate) {
        try {
            List<SupplierPayable> payables = payableRepository.findByInvoiceDateBetween(startDate, endDate);
            
            BigDecimal totalAmount = payables.stream()
                    .map(SupplierPayable::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalPaid = payables.stream()
                    .map(SupplierPayable::getPaidAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            BigDecimal totalRemaining = payables.stream()
                    .map(SupplierPayable::getRemainingAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Map<String, Object> report = new HashMap<>();
            report.put("payables", payables.stream().map(this::toResponse).collect(Collectors.toList()));
            report.put("totalAmount", totalAmount);
            report.put("totalPaid", totalPaid);
            report.put("totalRemaining", totalRemaining);
            report.put("count", payables.size());

            return ApiResponse.success("Báo cáo công nợ", report);
        } catch (Exception e) {
            log.error("Error getting payable report: {}", e.getMessage(), e);
            return ApiResponse.error("Lỗi khi lấy báo cáo công nợ");
        }
    }

    // Helper methods

    private SupplierPayableResponse toResponse(SupplierPayable payable) {
        Integer daysOverdue = null;
        if (payable.getStatus() == PayableStatus.OVERDUE) {
            daysOverdue = (int) ChronoUnit.DAYS.between(payable.getDueDate(), LocalDate.now());
        }

        return SupplierPayableResponse.builder()
                .id(payable.getId())
                .payableCode(payable.getPayableCode())
                .supplierId(payable.getSupplier().getId())
                .supplierName(payable.getSupplier().getName())
                .supplierTaxCode(payable.getSupplier().getTaxCode())
                .purchaseOrderId(payable.getPurchaseOrder().getId())
                .purchaseOrderCode(payable.getPurchaseOrder().getPoCode())
                .totalAmount(payable.getTotalAmount())
                .paidAmount(payable.getPaidAmount())
                .remainingAmount(payable.getRemainingAmount())
                .status(payable.getStatus())
                .invoiceDate(payable.getInvoiceDate())
                .dueDate(payable.getDueDate())
                .paymentTermDays(payable.getPaymentTermDays())
                .daysOverdue(daysOverdue)
                .note(payable.getNote())
                .build();
    }

    private String generatePayableCode() {
        String date = LocalDate.now().toString().replace("-", "");
        String random = String.format("%04d", new Random().nextInt(10000));
        return "AP-" + date + "-" + random;
    }

    private String generatePaymentCode() {
        String date = LocalDate.now().toString().replace("-", "");
        String random = String.format("%04d", new Random().nextInt(10000));
        return "PAY-" + date + "-" + random;
    }
}
