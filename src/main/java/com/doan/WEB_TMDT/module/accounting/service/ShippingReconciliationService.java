package com.doan.WEB_TMDT.module.accounting.service;

import com.doan.WEB_TMDT.module.accounting.dto.ShippingReconciliationResponse;

public interface ShippingReconciliationService {
    ShippingReconciliationResponse generateReconciliation(String startDate, String endDate);
}
