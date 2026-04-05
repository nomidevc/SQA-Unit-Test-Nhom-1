package com.doan.WEB_TMDT.module.accounting.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinancialTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String transactionCode;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type; // REVENUE, EXPENSE, REFUND
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionCategory category; // SALES, SHIPPING, PAYMENT_FEE, TAX, SUPPLIER_PAYMENT, etc.
    
    @Column(nullable = false)
    private Double amount;
    
    private Long orderId; // Reference to order if applicable
    
    private Long supplierId; // Reference to supplier if applicable
    
    @Column(length = 1000)
    private String description;
    
    @Column(nullable = false)
    private LocalDateTime transactionDate;
    
    private LocalDateTime createdAt;
    
    private String createdBy;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (transactionCode == null) {
            transactionCode = "TXN" + System.currentTimeMillis();
        }
    }
}
