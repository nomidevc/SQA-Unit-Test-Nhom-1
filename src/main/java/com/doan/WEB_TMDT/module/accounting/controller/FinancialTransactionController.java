package com.doan.WEB_TMDT.module.accounting.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.accounting.dto.FinancialTransactionRequest;
import com.doan.WEB_TMDT.module.accounting.service.FinancialTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/accounting/transactions")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FinancialTransactionController {

    private final FinancialTransactionService transactionService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(transactionService.getAllTransactions(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> getTransactionById(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getTransactionById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> createTransaction(
            @RequestBody FinancialTransactionRequest request,
            Authentication authentication
    ) {
        String createdBy = authentication.getName();
        return ResponseEntity.ok(transactionService.createTransaction(request, createdBy));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> updateTransaction(
            @PathVariable Long id,
            @RequestBody FinancialTransactionRequest request
    ) {
        return ResponseEntity.ok(transactionService.updateTransaction(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> deleteTransaction(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.deleteTransaction(id));
    }

    @PostMapping("/search")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'ACCOUNTANT')")
    public ResponseEntity<ApiResponse> searchTransactions(@RequestBody SearchRequest request) {
        return ResponseEntity.ok(transactionService.searchTransactions(request.getStartDate(), request.getEndDate()));
    }
    
    // Inner class for search request
    @lombok.Data
    public static class SearchRequest {
        private String startDate;
        private String endDate;
    }
}
