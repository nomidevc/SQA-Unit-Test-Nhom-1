package com.doan.WEB_TMDT.module.accounting.service;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.accounting.dto.FinancialTransactionRequest;
import org.springframework.data.domain.Pageable;

public interface FinancialTransactionService {
    ApiResponse getAllTransactions(Pageable pageable);
    ApiResponse getTransactionById(Long id);
    ApiResponse createTransaction(FinancialTransactionRequest request, String createdBy);
    ApiResponse updateTransaction(Long id, FinancialTransactionRequest request);
    ApiResponse deleteTransaction(Long id);
    ApiResponse searchTransactions(String startDate, String endDate);
}
