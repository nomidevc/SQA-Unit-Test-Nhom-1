package com.doan.WEB_TMDT.module.inventory.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.inventory.dto.*;
import com.doan.WEB_TMDT.module.inventory.service.InventoryService;
import com.doan.WEB_TMDT.module.product.entity.Product;
import com.doan.WEB_TMDT.module.product.repository.ProductRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {
    private final InventoryService inventoryService;
    private final ProductRepository productRepository;
    private final com.doan.WEB_TMDT.module.inventory.service.ProductSpecificationService productSpecificationService;
    private final com.doan.WEB_TMDT.module.inventory.repository.WarehouseProductRepository warehouseProductRepository;
    
    // ===== Warehouse Products =====
    @GetMapping("/warehouse-products")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse getWarehouseProducts() {
        var products = warehouseProductRepository.findAll();
        return ApiResponse.success("Danh sách sản phẩm kho", products);
    }
    
    @GetMapping("/warehouse-products/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse getWarehouseProduct(@PathVariable Long id) {
        var product = warehouseProductRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm #" + id));
        return ApiResponse.success("Chi tiết sản phẩm", product);
    }
    
    @PostMapping("/warehouse-products")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE', 'ADMIN')")
    public ApiResponse createWarehouseProduct(@Valid @RequestBody CreateWarehouseProductRequest req) {
        return inventoryService.createWarehouseProduct(req);
    }
    
    @PutMapping("/warehouse-products/{id}")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE', 'PRODUCT_MANAGER', 'ADMIN')")
    public ApiResponse updateWarehouseProduct(@PathVariable Long id, @Valid @RequestBody CreateWarehouseProductRequest req) {
        return inventoryService.updateWarehouseProduct(id, req);
    }
    
    // ===== Products =====
    @PostMapping("/create_pchaseOrder")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE', 'ADMIN')")
    public ApiResponse createPurchaseOrder(@Valid @RequestBody CreatePORequest req){
        return inventoryService.createPurchaseOrder(req);
    }
    // ===== Suppliers =====
    @GetMapping("/suppliers")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse getAllSuppliers() {
        return inventoryService.getAllSuppliers();
    }

    @PostMapping("/suppliers")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE', 'ADMIN')")
    public ApiResponse createSupplier(@Valid @RequestBody CreateSupplierRequest req) {
        return inventoryService.getOrCreateSupplier(req);
    }

    @GetMapping("/supplier/{supplierId}/products")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse getProductsBySupplier(@PathVariable Long supplierId) {
        List<Product> products = productRepository.findAllByWarehouseProduct_Supplier_Id(supplierId);
        return ApiResponse.success("Danh sách sản phẩm", products);
    }
    // ===== Import / Export =====
    @PostMapping("/import")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE', 'ADMIN')")
    public ApiResponse importStock(@Valid @RequestBody CompletePORequest req, Authentication auth) {
        String actor = auth != null ? auth.getName() : "system";
        return inventoryService.completePurchaseOrder(req);
    }

    @PostMapping("/create")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE', 'ADMIN')")
    public ApiResponse export(@RequestBody CreateExportOrderRequest req) {
        return inventoryService.createExportOrder(req);
    }
    
    @PostMapping("/export-for-sale")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE', 'ADMIN')")
    public ApiResponse exportForSale(@RequestBody SaleExportRequest req, Authentication auth) {
        // Set createdBy từ user đang đăng nhập
        String actor = auth != null ? auth.getName() : "system";
        req.setCreatedBy(actor);
        
        // Validate manually sau khi set createdBy
        if (req.getOrderId() == null) {
            return ApiResponse.error("Order ID không được để trống");
        }
        if (req.getReason() == null || req.getReason().isBlank()) {
            return ApiResponse.error("Lý do xuất kho không được để trống");
        }
        if (req.getItems() == null || req.getItems().isEmpty()) {
            return ApiResponse.error("Danh sách sản phẩm không được để trống");
        }
        
        return inventoryService.exportForSale(req);
    }
    
    @PostMapping("/export-for-warranty")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE', 'ADMIN')")
    public ApiResponse exportForWarranty(@Valid @RequestBody WarrantyExportRequest req, Authentication auth) {
        String actor = auth != null ? auth.getName() : "system";
        req.setCreatedBy(actor);
        return inventoryService.exportForWarranty(req);
    }

    // ===== Search by Specifications =====
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse searchBySpecs(@RequestParam String keyword) {
        var products = productSpecificationService.searchBySpecValue(keyword);
        return ApiResponse.success("Tìm thấy " + products.size() + " sản phẩm", products);
    }

    @GetMapping("/filter")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse filterBySpecs(
            @RequestParam String key,
            @RequestParam String value
    ) {
        var products = productSpecificationService.searchBySpecKeyAndValue(key, value);
        return ApiResponse.success("Tìm thấy " + products.size() + " sản phẩm", products);
    }

    // ===== Stock View (PRODUCT_MANAGER có thể xem, nhưng chỉ đọc) =====
    @GetMapping("/stock")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse getStocks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type
    ) {
        // Ưu tiên lấy 'status', nếu không có thì lấy 'type' (đề phòng frontend đặt tên khác)
        String filterParam = (status != null && !status.isEmpty()) ? status : type;

        return inventoryService.getStocks(filterParam);
    }

    @GetMapping("/stock/{id}/serials")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse getStockSerials(@PathVariable Long id) {
        return inventoryService.getStockDetails(id);
    }

    // ===== Transaction History =====
    @GetMapping("/purchase-orders")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse getPurchaseOrders(@RequestParam(required = false) String status) {
        com.doan.WEB_TMDT.module.inventory.entity.POStatus poStatus = null;
        if (status != null && !status.isEmpty()) {
            poStatus = com.doan.WEB_TMDT.module.inventory.entity.POStatus.valueOf(status);
        }
        return inventoryService.getPurchaseOrders(poStatus);
    }

    @GetMapping("/export-orders")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse getExportOrders(@RequestParam(required = false) String status) {
        com.doan.WEB_TMDT.module.inventory.entity.ExportStatus exportStatus = null;
        if (status != null && !status.isEmpty()) {
            exportStatus = com.doan.WEB_TMDT.module.inventory.entity.ExportStatus.valueOf(status);
        }
        return inventoryService.getExportOrders(exportStatus);
    }

    @GetMapping("/purchase-orders/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse getPurchaseOrderDetail(@PathVariable Long id) {
        return inventoryService.getPurchaseOrderDetail(id);
    }

    @GetMapping("/export-orders/{id}")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse getExportOrderDetail(@PathVariable Long id) {
        return inventoryService.getExportOrderDetail(id);
    }

    @PutMapping("/purchase-orders/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE', 'ADMIN')")
    public ApiResponse cancelPurchaseOrder(@PathVariable Long id) {
        return inventoryService.cancelPurchaseOrder(id);
    }

    @PutMapping("/export-orders/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('WAREHOUSE', 'ADMIN')")
    public ApiResponse cancelExportOrder(@PathVariable Long id) {
        return inventoryService.cancelExportOrder(id);
    }
}
