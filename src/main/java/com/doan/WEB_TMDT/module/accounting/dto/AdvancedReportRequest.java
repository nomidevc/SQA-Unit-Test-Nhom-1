package com.doan.WEB_TMDT.module.accounting.dto;

import lombok.Data;

@Data
public class AdvancedReportRequest {
    private String startDate;
    private String endDate;
    private String groupBy; // DAILY, MONTHLY, QUARTERLY
}
