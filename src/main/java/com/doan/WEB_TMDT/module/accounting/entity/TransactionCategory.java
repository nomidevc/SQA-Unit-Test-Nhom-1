package com.doan.WEB_TMDT.module.accounting.entity;

public enum TransactionCategory {
    SALES,              // Doanh thu bán hàng
    SHIPPING,           // Chi phí vận chuyển
    PAYMENT_FEE,        // Phí cổng thanh toán
    TAX,                // Thuế
    SUPPLIER_PAYMENT,   // Thanh toán nhà cung cấp
    REFUND,             // Hoàn tiền khách hàng
    OTHER_REVENUE,      // Doanh thu khác
    OTHER_EXPENSE       // Chi phí khác
}
