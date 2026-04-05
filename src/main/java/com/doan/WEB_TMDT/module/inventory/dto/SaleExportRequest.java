package com.doan.WEB_TMDT.module.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class SaleExportRequest {
    private String createdBy;        // Nhân viên thao tác xuất kho

    @NotNull
    private Long orderId;            // Mã đơn hàng cần xuất kho (liên kết module Order)

    @NotBlank
    private String reason;           // Ví dụ: "Giao hàng cho khách", "Khách đến lấy hàng"

    private String note;

    @NotEmpty
    @Valid
    private List<ExportItemRequest> items;   // SKU + serial
}