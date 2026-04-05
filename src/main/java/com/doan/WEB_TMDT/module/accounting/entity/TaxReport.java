package com.doan.WEB_TMDT.module.accounting.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tax_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaxReport {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String reportCode;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaxType taxType; // VAT, CORPORATE_TAX
    
    @Column(nullable = false)
    private LocalDate periodStart;
    
    @Column(nullable = false)
    private LocalDate periodEnd;
    
    private Double taxableRevenue; // Doanh thu chịu thuế
    
    private Double taxRate; // Thuế suất (%)
    
    private Double taxAmount; // Số thuế phải nộp
    
    private Double paidAmount; // Số thuế đã nộp
    
    private Double remainingTax; // Số thuế còn nợ
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private TaxStatus status = TaxStatus.DRAFT;
    
    private LocalDateTime submittedAt;
    
    private LocalDateTime paidAt;
    
    private LocalDateTime createdAt;
    
    private String createdBy;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (reportCode == null) {
            reportCode = "TAX" + System.currentTimeMillis();
        }
        if (remainingTax == null && taxAmount != null && paidAmount != null) {
            remainingTax = taxAmount - paidAmount;
        }
    }
}
