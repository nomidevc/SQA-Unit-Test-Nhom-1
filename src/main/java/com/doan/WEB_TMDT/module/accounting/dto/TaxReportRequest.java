package com.doan.WEB_TMDT.module.accounting.dto;

import com.doan.WEB_TMDT.module.accounting.entity.TaxType;
import lombok.Data;

@Data
public class TaxReportRequest {
    private TaxType taxType;
    private String periodStart;
    private String periodEnd;
    private Double taxableRevenue;
    private Double taxRate;
}
