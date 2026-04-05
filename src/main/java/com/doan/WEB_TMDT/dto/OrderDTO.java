package com.doan.WEB_TMDT.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {
    private Long id;
    private String orderCode;
    private Double totalAmount;
    private String status;
    private LocalDateTime createdAt;
    private String customerName;
    private String customerEmail;
}
