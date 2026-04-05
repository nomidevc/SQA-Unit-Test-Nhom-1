package com.doan.WEB_TMDT.module.accounting.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounting_periods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountingPeriod {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name; // e.g., "Tháng 12/2024"
    
    @Column(nullable = false)
    private LocalDate startDate;
    
    @Column(nullable = false)
    private LocalDate endDate;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PeriodStatus status = PeriodStatus.OPEN;
    
    private Double totalRevenue;
    
    private Double totalExpense;
    
    private Double netProfit;
    
    private Double discrepancyRate; // Tỷ lệ sai lệch (%)
    
    private LocalDateTime closedAt;
    
    private String closedBy;
    
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
