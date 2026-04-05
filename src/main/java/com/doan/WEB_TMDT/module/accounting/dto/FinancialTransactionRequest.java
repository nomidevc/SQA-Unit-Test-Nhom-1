package com.doan.WEB_TMDT.module.accounting.dto;

import com.doan.WEB_TMDT.module.accounting.entity.TransactionCategory;
import com.doan.WEB_TMDT.module.accounting.entity.TransactionType;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FinancialTransactionRequest {
    private TransactionType type;
    private TransactionCategory category;
    private Double amount;
    private Long orderId;
    private Long supplierId;
    private String description;
    private LocalDateTime transactionDate;
}
