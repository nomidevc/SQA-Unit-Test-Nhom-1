package com.doan.WEB_TMDT.module.accounting.service.impl;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.accounting.dto.FinancialTransactionRequest;
import com.doan.WEB_TMDT.module.accounting.dto.FinancialTransactionResponse;
import com.doan.WEB_TMDT.module.accounting.entity.FinancialTransaction;
import com.doan.WEB_TMDT.module.accounting.repository.FinancialTransactionRepository;
import com.doan.WEB_TMDT.module.accounting.service.FinancialTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinancialTransactionServiceImpl implements FinancialTransactionService {

    private final FinancialTransactionRepository transactionRepository;

    @Override
    public ApiResponse getAllTransactions(Pageable pageable) {
        Page<FinancialTransaction> transactions = transactionRepository.findAllByOrderByTransactionDateDesc(pageable);
        Page<FinancialTransactionResponse> response = transactions.map(this::toResponse);
        return ApiResponse.success("Lấy danh sách giao dịch thành công", response);
    }

    @Override
    public ApiResponse getTransactionById(Long id) {
        FinancialTransaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch"));
        return ApiResponse.success("Lấy thông tin giao dịch thành công", toResponse(transaction));
    }

    @Override
    @Transactional
    public ApiResponse createTransaction(FinancialTransactionRequest request, String createdBy) {
        FinancialTransaction transaction = FinancialTransaction.builder()
                .type(request.getType())
                .category(request.getCategory())
                .amount(request.getAmount())
                .orderId(request.getOrderId())
                .supplierId(request.getSupplierId())
                .description(request.getDescription())
                .transactionDate(request.getTransactionDate() != null ? request.getTransactionDate() : LocalDateTime.now())
                .createdBy(createdBy)
                .build();

        FinancialTransaction saved = transactionRepository.save(transaction);
        return ApiResponse.success("Tạo giao dịch thành công", toResponse(saved));
    }

    @Override
    @Transactional
    public ApiResponse updateTransaction(Long id, FinancialTransactionRequest request) {
        FinancialTransaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy giao dịch"));

        transaction.setType(request.getType());
        transaction.setCategory(request.getCategory());
        transaction.setAmount(request.getAmount());
        transaction.setOrderId(request.getOrderId());
        transaction.setSupplierId(request.getSupplierId());
        transaction.setDescription(request.getDescription());
        transaction.setTransactionDate(request.getTransactionDate());

        FinancialTransaction updated = transactionRepository.save(transaction);
        return ApiResponse.success("Cập nhật giao dịch thành công", toResponse(updated));
    }

    @Override
    @Transactional
    public ApiResponse deleteTransaction(Long id) {
        if (!transactionRepository.existsById(id)) {
            return ApiResponse.error("Không tìm thấy giao dịch");
        }
        transactionRepository.deleteById(id);
        return ApiResponse.success("Xóa giao dịch thành công", null);
    }

    @Override
    public ApiResponse searchTransactions(String startDate, String endDate) {
        try {
            LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
            LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");
            
            List<FinancialTransaction> transactions = transactionRepository.findByTransactionDateBetween(start, end);
            List<FinancialTransactionResponse> response = transactions.stream()
                    .map(this::toResponse)
                    .collect(Collectors.toList());
            return ApiResponse.success("Tìm kiếm giao dịch thành công", response);
        } catch (Exception e) {
            return ApiResponse.error("Lỗi khi tìm kiếm: " + e.getMessage());
        }
    }

    private FinancialTransactionResponse toResponse(FinancialTransaction transaction) {
        return FinancialTransactionResponse.builder()
                .id(transaction.getId())
                .transactionCode(transaction.getTransactionCode())
                .type(transaction.getType())
                .category(transaction.getCategory())
                .amount(transaction.getAmount())
                .orderId(transaction.getOrderId())
                .supplierId(transaction.getSupplierId())
                .description(transaction.getDescription())
                .transactionDate(transaction.getTransactionDate())
                .createdAt(transaction.getCreatedAt())
                .createdBy(transaction.getCreatedBy())
                .build();
    }
}
