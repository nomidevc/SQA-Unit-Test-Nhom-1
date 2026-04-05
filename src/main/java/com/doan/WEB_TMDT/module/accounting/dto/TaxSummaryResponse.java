package com.doan.WEB_TMDT.module.accounting.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TaxSummaryResponse {
    private Double vatOwed;        // VAT còn nợ
    private Double vatPaid;        // VAT đã nộp
    private Double corporateOwed;  // Thuế TNDN còn nợ
    private Double corporatePaid;  // Thuế TNDN đã nộp
    private Double totalOwed;      // Tổng nợ thuế
    private Double totalPaid;      // Tổng đã nộp
}
