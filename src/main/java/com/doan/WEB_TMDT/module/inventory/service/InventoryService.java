package com.doan.WEB_TMDT.module.inventory.service;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.inventory.dto.*;
import com.doan.WEB_TMDT.module.inventory.entity.POStatus;
import com.doan.WEB_TMDT.module.inventory.entity.ExportStatus;

public interface InventoryService {
    ApiResponse getAllSuppliers();
    ApiResponse getOrCreateSupplier(CreateSupplierRequest req);
    ApiResponse createWarehouseProduct(CreateWarehouseProductRequest req);
    ApiResponse updateWarehouseProduct(Long id, CreateWarehouseProductRequest req);
    ApiResponse createPurchaseOrder(CreatePORequest req);
    ApiResponse completePurchaseOrder(CompletePORequest req);
    ApiResponse createExportOrder(CreateExportOrderRequest req);
    ApiResponse getPurchaseOrders(POStatus status);
    ApiResponse getExportOrders(ExportStatus status);
    ApiResponse getPurchaseOrderDetail(Long id);
    ApiResponse getExportOrderDetail(Long id);
    ApiResponse cancelPurchaseOrder(Long id);
    ApiResponse cancelExportOrder(Long id);
    ApiResponse getStocks(String status);
    ApiResponse getStockDetails(Long warehouseProductId);
    ApiResponse exportForSale(SaleExportRequest req);
    ApiResponse exportForWarranty(WarrantyExportRequest req);
    void syncReservedQuantity(Long warehouseProductId, Long newReserved);
}
