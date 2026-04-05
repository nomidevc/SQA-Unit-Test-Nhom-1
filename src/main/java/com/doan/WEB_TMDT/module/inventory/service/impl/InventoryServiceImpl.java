package com.doan.WEB_TMDT.module.inventory.service.impl;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.inventory.dto.*;
// ❌ Dòng này đã bị xóa/thay thế vì nó xung đột với ProductDetail của Product module:
// import com.doan.WEB_TMDT.module.inventory.entity.ProductDetail;
// Giữ lại
import com.doan.WEB_TMDT.module.inventory.entity.*;
import com.doan.WEB_TMDT.module.inventory.repository.*;
import com.doan.WEB_TMDT.module.inventory.service.InventoryService;
import com.doan.WEB_TMDT.module.product.entity.Product;
import com.doan.WEB_TMDT.module.product.repository.ProductRepository;

// 💡 Thêm import entity ProductDetail đúng từ Product module
import lombok.extern.slf4j.Slf4j;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {
    private final ExportOrderRepository exportOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final WarehouseProductRepository warehouseProductRepository;
    private final ProductDetailRepository productDetailRepository;
    private final InventoryStockRepository inventoryStockRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final com.doan.WEB_TMDT.module.inventory.service.ProductSpecificationService productSpecificationService;
    private final com.doan.WEB_TMDT.module.order.repository.OrderRepository orderRepository;
    private final ExportOrderItemRepository exportOrderItemRepository;
    private final com.doan.WEB_TMDT.module.accounting.service.SupplierPayableService supplierPayableService;
    private final com.doan.WEB_TMDT.module.shipping.service.ShippingService shippingService;

    private String generateExportCode() {
        return "PX" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + String.format("%03d", new Random().nextInt(999));
    }

    @Override
    public ApiResponse getAllSuppliers() {
        List<Supplier> suppliers = supplierRepository.findAll();
        return ApiResponse.success("Danh sách nhà cung cấp", suppliers);
    }

    @Override
    public ApiResponse getOrCreateSupplier(CreateSupplierRequest req){

        if (req.getTaxCode() != null) {
            Optional<Supplier> byTax = supplierRepository.findByTaxCode(req.getTaxCode());
            if (byTax.isPresent()) {
                return ApiResponse.success("OK", byTax.get());
            }
        }

        if (req.getEmail() != null) {
            Optional<Supplier> byEmail = supplierRepository.findByEmail(req.getEmail());
            if (byEmail.isPresent()) {
                return ApiResponse.success("OK", byEmail.get());
            }
        }

        if (req.getPhone() != null) {
            Optional<Supplier> byPhone = supplierRepository.findByPhone(req.getPhone());
            if (byPhone.isPresent()) {
                return ApiResponse.success("OK", byPhone.get());
            }
        }
        Supplier supplier = Supplier.builder()
                .name(req.getName())
                .taxCode(req.getTaxCode())
                .email(req.getEmail())
                .phone(req.getPhone())
                .address(req.getAddress())
                .bankAccount(req.getBankAccount())
                .paymentTerm(req.getPaymentTerm())
                .paymentTermDays(req.getPaymentTermDays())
                .active(true)
                .autoCreated(true)
                .build();
        Supplier savedSupplier = supplierRepository.save(supplier);
        return ApiResponse.success("OK", savedSupplier);

    }

    @Override
    public ApiResponse createWarehouseProduct(CreateWarehouseProductRequest req) {
        // Check if SKU already exists
        Optional<WarehouseProduct> existing = warehouseProductRepository.findBySku(req.getSku());
        if (existing.isPresent()) {
            return ApiResponse.error("SKU đã tồn tại: " + req.getSku());
        }

        // Get supplier if provided
        Supplier supplier = null;
        if (req.getSupplierId() != null) {
            supplier = supplierRepository.findById(req.getSupplierId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhà cung cấp #" + req.getSupplierId()));
        }

        // Create warehouse product
        WarehouseProduct wp = WarehouseProduct.builder()
                .sku(req.getSku())
                .internalName(req.getInternalName())
                .supplier(supplier)
                .description(req.getDescription())
                .techSpecsJson(req.getTechSpecsJson() != null ? req.getTechSpecsJson() : "{}")
                .lastImportDate(LocalDateTime.now())
                .build();

        WarehouseProduct saved = warehouseProductRepository.save(wp);

        // Parse and save specifications
        productSpecificationService.parseAndSaveSpecs(saved);

        return ApiResponse.success("Tạo sản phẩm kho thành công", saved);
    }

    @Override
    public ApiResponse updateWarehouseProduct(Long id, CreateWarehouseProductRequest req) {
        // Find existing product
        WarehouseProduct wp = warehouseProductRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm #" + id));

        // Check if SKU is being changed and already exists
        if (!wp.getSku().equals(req.getSku())) {
            Optional<WarehouseProduct> existing = warehouseProductRepository.findBySku(req.getSku());
            if (existing.isPresent()) {
                return ApiResponse.error("SKU đã tồn tại: " + req.getSku());
            }
            wp.setSku(req.getSku());
        }

        // Get supplier if provided
        if (req.getSupplierId() != null) {
            Supplier supplier = supplierRepository.findById(req.getSupplierId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy nhà cung cấp #" + req.getSupplierId()));
            wp.setSupplier(supplier);
        }

        // Update fields
        wp.setInternalName(req.getInternalName());
        wp.setDescription(req.getDescription());
        wp.setTechSpecsJson(req.getTechSpecsJson() != null ? req.getTechSpecsJson() : "{}");

        WarehouseProduct updated = warehouseProductRepository.save(wp);

        // Re-parse and save specifications
        productSpecificationService.parseAndSaveSpecs(updated);

        return ApiResponse.success("Cập nhật sản phẩm kho thành công", updated);
    }


    @Override
    public ApiResponse createPurchaseOrder(CreatePORequest req) {
        // 1️⃣ Kiểm tra dữ liệu đầu vào
        if (req.getSupplier() == null || req.getSupplier().getTaxCode() == null) {
            throw new IllegalArgumentException("Thiếu thông tin nhà cung cấp hoặc mã số thuế.");
        }

        CreateSupplierRequest sreq = req.getSupplier();

        // 2️⃣ Tìm NCC theo mã số thuế
        Supplier supplier = supplierRepository.findByTaxCode(sreq.getTaxCode())
                .orElseGet(() -> {
                    log.info("🆕 Tạo nhà cung cấp mới với mã số thuế: {}", sreq.getTaxCode());
                    return supplierRepository.save(
                            Supplier.builder()
                                    .name(sreq.getName())
                                    .contactName(sreq.getContactName())
                                    .taxCode(sreq.getTaxCode())
                                    .email(sreq.getEmail())
                                    .phone(sreq.getPhone())
                                    .address(sreq.getAddress())
                                    .bankAccount(sreq.getBankAccount())
                                    .paymentTerm(sreq.getPaymentTerm())
                                    .paymentTermDays(sreq.getPaymentTermDays())
                                    .active(true)
                                    .autoCreated(true)
                                    .build()
                    );
                });

        // 3️⃣ Tạo phiếu nhập hàng (chỉ gắn theo taxCode)
        PurchaseOrder po = PurchaseOrder.builder()
                .poCode(req.getPoCode())
                .supplier(supplier) // join qua tax_code
                .status(POStatus.CREATED)
                .orderDate(LocalDateTime.now())
                .createdBy(req.getCreatedBy())
                .note(req.getNote())
                .build();

        // 4️⃣ Gắn sản phẩm — tạo WarehouseProduct nếu chưa có
        List<PurchaseOrderItem> items = req.getItems().stream().map(i -> {
            WarehouseProduct wp = warehouseProductRepository.findBySku(i.getSku())
                    .orElseGet(() -> {
                        log.info("🆕 Tạo WarehouseProduct mới cho SKU: {}", i.getSku());

                        // Lấy thông tin từ request
                        String internalName = i.getInternalName() != null && !i.getInternalName().isEmpty()
                                ? i.getInternalName()
                                : "Sản phẩm mới - " + i.getSku();

                        String techSpecs = i.getTechSpecsJson() != null && !i.getTechSpecsJson().isEmpty()
                                ? i.getTechSpecsJson()
                                : "{}";

                        WarehouseProduct newWp = WarehouseProduct.builder()
                                .sku(i.getSku())
                                .internalName(internalName)
                                .supplier(supplier)
                                .lastImportDate(LocalDateTime.now())
                                .description(i.getNote())
                                .techSpecsJson(techSpecs)
                                .build();
                        WarehouseProduct savedWp = warehouseProductRepository.save(newWp);

                        // Parse và lưu specifications vào bảng riêng
                        productSpecificationService.parseAndSaveSpecs(savedWp);

                        return savedWp;
                    });

            return PurchaseOrderItem.builder()
                    .purchaseOrder(po)
                    .sku(i.getSku())
                    .warehouseProduct(wp) // ✅ luôn có giá trị
                    .quantity(i.getQuantity())
                    .unitCost(i.getUnitCost())
                    .warrantyMonths(i.getWarrantyMonths())
                    .note(i.getNote())
                    .build();
        }).toList();

        po.setItems(items);
        purchaseOrderRepository.save(po);

        return ApiResponse.success("Tạo phiếu nhập hàng thành công", po);
    }


    @Override
    @Transactional
    public ApiResponse completePurchaseOrder(CompletePORequest req) {
        try {
            return doCompletePurchaseOrder(req);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.error("Lỗi trùng lặp serial khi nhập hàng", e);
            String message = e.getMessage();
            if (message != null && message.contains("Duplicate entry")) {
                // Extract serial number from error message
                return ApiResponse.error("Serial bị trùng lặp! Vui lòng kiểm tra lại các serial đã nhập.");
            }
            return ApiResponse.error("Lỗi dữ liệu: " + e.getMessage());
        }
    }

    private ApiResponse doCompletePurchaseOrder(CompletePORequest req) {
        // 1️⃣ Lấy phiếu nhập hàng
        PurchaseOrder po = purchaseOrderRepository.findById(req.getPoId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu nhập #" + req.getPoId()));

        if (po.getStatus() != POStatus.CREATED) {
            return ApiResponse.error("Phiếu nhập #" + req.getPoId() + " không ở trạng thái chờ nhập hàng (CREATED).");
        }

        // 2️⃣ Duyệt từng sản phẩm trong request
        for (ProductSerialRequest serialReq : req.getSerials()) {
            String sku = serialReq.getProductSku();

            // Tìm dòng item trong PO tương ứng với SKU
            PurchaseOrderItem item = po.getItems().stream()
                    .filter(i -> i.getSku().equals(sku))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Sản phẩm SKU " + sku + " không thuộc phiếu nhập #" + po.getId()));

            // Lấy WarehouseProduct (đã được tạo sẵn khi tạo PO)
            WarehouseProduct wp = item.getWarehouseProduct();
            if (wp == null) {
                throw new IllegalStateException("WarehouseProduct không tồn tại cho SKU: " + sku);
            }

            // 3️⃣ Kiểm tra số lượng serial có khớp số lượng đặt
            if (serialReq.getSerialNumbers().size() != item.getQuantity()) {
                throw new RuntimeException("Số serial (" + serialReq.getSerialNumbers().size() +
                        ") không khớp với số lượng nhập (" + item.getQuantity() + ") cho SKU: " + sku);
            }

            // 4️⃣ Kiểm tra trùng serial
            for (String sn : serialReq.getSerialNumbers()) {
                if (sn == null || sn.trim().isEmpty()) {
                    throw new RuntimeException("Serial không được để trống cho SKU: " + sku);
                }
                if (productDetailRepository.existsBySerialNumber(sn)) {
                    throw new RuntimeException("Serial " + sn + " đã tồn tại trong hệ thống! Vui lòng kiểm tra lại.");
                }
            }
            final WarehouseProduct finalWp = wp;


            // 5️⃣ Tạo danh sách ProductDetail (serial cụ thể)
            List<ProductDetail> details = serialReq.getSerialNumbers().stream()
                    .map(sn -> ProductDetail.builder()
                            .serialNumber(sn)
                            .importPrice(item.getUnitCost())
                            .importDate(LocalDateTime.now())
                            .warrantyMonths(item.getWarrantyMonths())
                            .status(ProductStatus.IN_STOCK)
                            .warehouseProduct(finalWp )
                            .purchaseOrderItem(item)
                            .build())
                    .toList();

            // Gắn vào item và lưu
            if (item.getProductDetails() == null)
                item.setProductDetails(new ArrayList<>());
            item.getProductDetails().addAll(details);

            // 6️⃣ Cập nhật tồn kho
            InventoryStock stock = inventoryStockRepository.findByWarehouseProduct_Id(wp.getId())
                    .orElse(InventoryStock.builder()
                            .warehouseProduct(wp)
                            .onHand(0L)
                            .reserved(0L)
                            .damaged(0L)
                            .build());

            stock.setOnHand(stock.getOnHand() + details.size());
            inventoryStockRepository.save(stock);

            // 7️⃣ Đồng bộ với bảng Product
            syncStockWithProduct(wp, stock.getOnHand());
        }

        // 7️⃣ Cập nhật phiếu nhập
        po.setReceivedDate(req.getReceivedDate());
        po.setStatus(POStatus.RECEIVED);
        PurchaseOrder savedPo = purchaseOrderRepository.save(po);

        // 8️⃣ Tạo công nợ nhà cung cấp
        try {
            ApiResponse payableResponse = supplierPayableService.createPayableFromPurchaseOrder(savedPo);
            if (payableResponse.isSuccess()) {
                log.info("Created supplier payable for PO: {}", savedPo.getPoCode());
            } else {
                log.warn("Failed to create payable for PO {}: {}", savedPo.getPoCode(), payableResponse.getMessage());
            }
        } catch (Exception e) {
            log.error("Error creating payable for PO {}: {}", savedPo.getPoCode(), e.getMessage(), e);
            // Không throw exception để không ảnh hưởng đến việc nhập hàng
        }

        return ApiResponse.success("Hoàn tất nhập hàng thành công!", po.getId());
    }


    @Override
    @Transactional
    public ApiResponse createExportOrder(CreateExportOrderRequest req) {

        // 1️⃣ Tạo phiếu xuất
        ExportOrder exportOrder = ExportOrder.builder()
                .exportCode(generateExportCode())
                .exportDate(LocalDateTime.now())
                .createdBy(req.getCreatedBy())
                .reason(req.getReason())
                .note(req.getNote())
                .status(ExportStatus.CREATED)
                .build();

        List<ExportOrderItem> exportItems = new ArrayList<>();

        // 2️⃣ Duyệt từng sản phẩm trong danh sách xuất
        for (ExportItemRequest itemReq : req.getItems()) {

            WarehouseProduct product = warehouseProductRepository.findBySku(itemReq.getProductSku())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm SKU: " + itemReq.getProductSku()));

            int exportCount = itemReq.getSerialNumbers().size();
            double totalCost = 0;

            // 3️⃣ Kiểm tra tồn kho
            InventoryStock stock = inventoryStockRepository.findByWarehouseProduct_Id(product.getId())
                    .orElseThrow(() -> new RuntimeException("Không có dữ liệu tồn kho cho sản phẩm: " + product.getSku()));

            if (stock.getOnHand() < exportCount) {
                throw new RuntimeException("Không đủ hàng trong kho. Sẵn có: " + stock.getOnHand() +
                        ", yêu cầu xuất: " + exportCount + " (" + product.getSku() + ")");
            }

            // 4️⃣ Xử lý từng serial: cập nhật trạng thái & tính giá vốn
            for (String serial : itemReq.getSerialNumbers()) {
                ProductDetail detail = productDetailRepository.findBySerialNumber(serial)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy serial: " + serial));

                if (detail.getStatus() != ProductStatus.IN_STOCK) {
                    throw new RuntimeException("Serial " + serial + " không ở trạng thái IN_STOCK, không thể xuất kho!");
                }

                // cập nhật trạng thái serial
                detail.setStatus(ProductStatus.SOLD);
                detail.setSoldDate(LocalDateTime.now());
                productDetailRepository.save(detail);

                // cộng dồn giá nhập thật
                totalCost += detail.getImportPrice();
            }

            // 5️⃣ Cập nhật tồn kho
            stock.setOnHand(stock.getOnHand() - exportCount);
            inventoryStockRepository.save(stock);

            // Đồng bộ với bảng Product
            syncStockWithProduct(product, stock.getOnHand());

            // 6️⃣ Ghi dòng chi tiết phiếu xuất
            ExportOrderItem item = ExportOrderItem.builder()
                    .exportOrder(exportOrder)
                    .warehouseProduct(product)
                    .sku(product.getSku())
                    .quantity((long) exportCount)
                    .serialNumbers(String.join(",", itemReq.getSerialNumbers()))
                    .totalCost(totalCost)
                    .build();

            exportItems.add(item);
        }

        // 7️⃣ Lưu phiếu xuất
        exportOrder.setItems(exportItems);
        exportOrder.setStatus(ExportStatus.COMPLETED);
        exportOrderRepository.save(exportOrder);

        return ApiResponse.success("Xuất kho thành công!", exportOrder.getExportCode());
    }

    @Override
    public ApiResponse getPurchaseOrders(POStatus status) {
        List<PurchaseOrder> orders;
        if (status != null) {
            orders = purchaseOrderRepository.findByStatus(status);
        } else {
            orders = purchaseOrderRepository.findAll();
        }
        
        // Map to DTO with totalAmount and supplierName
        List<com.doan.WEB_TMDT.module.inventory.dto.PurchaseOrderListResponse> orderDTOs = orders.stream()
                .map(po -> {
                    // Calculate total amount
                    double totalAmount = po.getItems() != null ? po.getItems().stream()
                            .mapToDouble(item -> (item.getUnitCost() != null ? item.getUnitCost() : 0.0) * 
                                                (item.getQuantity() != null ? item.getQuantity() : 0L))
                            .sum() : 0.0;
                    
                    return com.doan.WEB_TMDT.module.inventory.dto.PurchaseOrderListResponse.builder()
                            .id(po.getId())
                            .poCode(po.getPoCode())
                            .supplierName(po.getSupplier() != null ? po.getSupplier().getName() : "N/A")
                            .orderDate(po.getOrderDate())
                            .receivedDate(po.getReceivedDate())
                            .status(po.getStatus().name())
                            .totalAmount(totalAmount)
                            .itemCount(po.getItems() != null ? po.getItems().size() : 0)
                            .build();
                })
                .toList();
        
        return ApiResponse.success("Danh sách phiếu nhập", orderDTOs);
    }

    @Override
    public ApiResponse getExportOrders(ExportStatus status) {
        List<ExportOrder> orders;
        if (status != null) {
            orders = exportOrderRepository.findByStatus(status);
        } else {
            orders = exportOrderRepository.findAll();
        }
        return ApiResponse.success("Danh sách phiếu xuất", orders);
    }

    @Override
    public ApiResponse getPurchaseOrderDetail(Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu nhập #" + id));

        // Map to DTO to avoid circular reference
        com.doan.WEB_TMDT.module.inventory.dto.PurchaseOrderDetailResponse dto = mapToPurchaseOrderDetailDTO(po);
        return ApiResponse.success("Chi tiết phiếu nhập", dto);
    }

    private com.doan.WEB_TMDT.module.inventory.dto.PurchaseOrderDetailResponse mapToPurchaseOrderDetailDTO(PurchaseOrder po) {
        // Map supplier
        com.doan.WEB_TMDT.module.inventory.dto.PurchaseOrderDetailResponse.SupplierInfo supplierInfo = null;
        if (po.getSupplier() != null) {
            supplierInfo = com.doan.WEB_TMDT.module.inventory.dto.PurchaseOrderDetailResponse.SupplierInfo.builder()
                    .id(po.getSupplier().getId())
                    .name(po.getSupplier().getName())
                    .taxCode(po.getSupplier().getTaxCode())
                    .contactPerson(po.getSupplier().getContactName())
                    .phone(po.getSupplier().getPhone())
                    .email(po.getSupplier().getEmail())
                    .address(po.getSupplier().getAddress())
                    .bankAccount(po.getSupplier().getBankAccount())
                    .paymentTerm(po.getSupplier().getPaymentTerm())
                    .build();
        }

        // Map items
        List<com.doan.WEB_TMDT.module.inventory.dto.PurchaseOrderDetailResponse.PurchaseOrderItemInfo> itemInfos =
                po.getItems().stream().map(item -> {
                    // Map warehouse product
                    com.doan.WEB_TMDT.module.inventory.dto.PurchaseOrderDetailResponse.WarehouseProductInfo wpInfo = null;
                    if (item.getWarehouseProduct() != null) {
                        WarehouseProduct wp = item.getWarehouseProduct();
                        wpInfo = com.doan.WEB_TMDT.module.inventory.dto.PurchaseOrderDetailResponse.WarehouseProductInfo.builder()
                                .id(wp.getId())
                                .sku(wp.getSku())
                                .internalName(wp.getInternalName())
                                .description(wp.getDescription())
                                .techSpecsJson(wp.getTechSpecsJson())
                                .build();
                    }

                    // Map product details (serials)
                    List<com.doan.WEB_TMDT.module.inventory.dto.PurchaseOrderDetailResponse.ProductDetailInfo> detailInfos = null;
                    if (item.getProductDetails() != null) {
                        detailInfos = item.getProductDetails().stream()
                                .map(detail -> com.doan.WEB_TMDT.module.inventory.dto.PurchaseOrderDetailResponse.ProductDetailInfo.builder()
                                        .id(detail.getId())
                                        .serialNumber(detail.getSerialNumber())
                                        .importPrice(detail.getImportPrice())
                                        .importDate(detail.getImportDate())
                                        .status(detail.getStatus().name())
                                        .warrantyMonths(detail.getWarrantyMonths())
                                        .build())
                                .toList();
                    }

                    return com.doan.WEB_TMDT.module.inventory.dto.PurchaseOrderDetailResponse.PurchaseOrderItemInfo.builder()
                            .id(item.getId())
                            .sku(item.getSku())
                            .quantity(item.getQuantity().intValue())
                            .unitCost(item.getUnitCost())
                            .warrantyMonths(item.getWarrantyMonths())
                            .note(item.getNote())
                            .warehouseProduct(wpInfo)
                            .productDetails(detailInfos)
                            .build();
                }).toList();

        // Tính tổng tiền
        double totalAmount = po.getItems().stream()
                .mapToDouble(item -> (item.getUnitCost() != null ? item.getUnitCost() : 0.0) * 
                                    (item.getQuantity() != null ? item.getQuantity() : 0L))
                .sum();

        return com.doan.WEB_TMDT.module.inventory.dto.PurchaseOrderDetailResponse.builder()
                .id(po.getId())
                .poCode(po.getPoCode())
                .status(po.getStatus().name())
                .orderDate(po.getOrderDate())
                .receivedDate(po.getReceivedDate())
                .createdBy(po.getCreatedBy())
                .note(po.getNote())
                .totalAmount(totalAmount)
                .supplier(supplierInfo)
                .items(itemInfos)
                .build();
    }

    @Override
    public ApiResponse getExportOrderDetail(Long id) {
        ExportOrder eo = exportOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu xuất #" + id));

        // Map to DTO
        com.doan.WEB_TMDT.module.inventory.dto.ExportOrderDetailResponse dto = mapToExportOrderDetailDTO(eo);
        return ApiResponse.success("Chi tiết phiếu xuất", dto);
    }

    private com.doan.WEB_TMDT.module.inventory.dto.ExportOrderDetailResponse mapToExportOrderDetailDTO(ExportOrder eo) {
        List<com.doan.WEB_TMDT.module.inventory.dto.ExportOrderDetailResponse.ExportOrderItemInfo> itemInfos =
                eo.getItems().stream().map(item -> {
                    // Map warehouse product
                    com.doan.WEB_TMDT.module.inventory.dto.ExportOrderDetailResponse.WarehouseProductInfo wpInfo = null;
                    if (item.getWarehouseProduct() != null) {
                        wpInfo = com.doan.WEB_TMDT.module.inventory.dto.ExportOrderDetailResponse.WarehouseProductInfo.builder()
                                .id(item.getWarehouseProduct().getId())
                                .sku(item.getWarehouseProduct().getSku())
                                .internalName(item.getWarehouseProduct().getInternalName())
                                .description(item.getWarehouseProduct().getDescription())
                                .techSpecsJson(item.getWarehouseProduct().getTechSpecsJson())
                                .build();
                    }

                    // Parse serial numbers
                    List<String> serialNumbers = item.getSerialNumbers() != null
                            ? List.of(item.getSerialNumbers().split(","))
                            : List.of();

                    return com.doan.WEB_TMDT.module.inventory.dto.ExportOrderDetailResponse.ExportOrderItemInfo.builder()
                            .id(item.getId())
                            .sku(item.getSku())
                            .quantity(item.getQuantity())
                            .totalCost(item.getTotalCost())
                            .serialNumbers(serialNumbers)
                            .warehouseProduct(wpInfo)
                            .build();
                }).toList();

        return com.doan.WEB_TMDT.module.inventory.dto.ExportOrderDetailResponse.builder()
                .id(eo.getId())
                .exportCode(eo.getExportCode())
                .status(eo.getStatus().name())
                .exportDate(eo.getExportDate())
                .createdBy(eo.getCreatedBy())
                .reason(eo.getReason())
                .note(eo.getNote())
                .items(itemInfos)
                .build();
    }

    @Override
    @Transactional
    public ApiResponse cancelPurchaseOrder(Long id) {
        PurchaseOrder po = purchaseOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu nhập #" + id));

        if (po.getStatus() != POStatus.CREATED) {
            return ApiResponse.error("Chỉ có thể hủy phiếu ở trạng thái chờ xử lý");
        }

        po.setStatus(POStatus.CANCELLED);
        purchaseOrderRepository.save(po);

        return ApiResponse.success("Đã hủy phiếu nhập thành công", po);
    }

    @Override
    @Transactional
    public ApiResponse cancelExportOrder(Long id) {
        ExportOrder eo = exportOrderRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy phiếu xuất #" + id));

        if (eo.getStatus() != ExportStatus.CREATED) {
            return ApiResponse.error("Chỉ có thể hủy phiếu ở trạng thái chờ xử lý");
        }

        eo.setStatus(ExportStatus.CANCELLED);
        exportOrderRepository.save(eo);

        return ApiResponse.success("Đã hủy phiếu xuất thành công", eo);
    }

    // ✅ ĐÃ SỬA: Thêm tham số status và logic lọc + Tối ưu performance
    @Override
    public ApiResponse getStocks(String status) {
        try {
            List<InventoryStock> stocks;

            // Xử lý lọc
            if ("low_stock".equals(status)) {
                stocks = inventoryStockRepository.findLowStockItems(10L);
            } else if ("out_of_stock".equals(status)) {
                stocks = inventoryStockRepository.findAll().stream()
                        .filter(s -> s.getOnHand() <= 0)
                        .toList();
            } else {
                stocks = inventoryStockRepository.findAll();
            }

            // Map sang DTO - CHỈ LẤY FIELD CẦN THIẾT
            List<Map<String, Object>> stockData = stocks.stream().map(stock -> {
                Map<String, Object> data = new HashMap<>();
                data.put("id", stock.getId());
                data.put("onHand", stock.getOnHand() != null ? stock.getOnHand() : 0L);
                data.put("reserved", stock.getReserved() != null ? stock.getReserved() : 0L);
                data.put("damaged", stock.getDamaged() != null ? stock.getDamaged() : 0L);
                data.put("sellable", stock.getSellable());
                data.put("available", stock.getAvailable());

                // Chỉ lấy thông tin cơ bản của warehouse product (không load supplier, techSpecs)
                if (stock.getWarehouseProduct() != null) {
                    WarehouseProduct wp = stock.getWarehouseProduct();
                    Map<String, Object> productInfo = new HashMap<>();
                    productInfo.put("id", wp.getId());
                    productInfo.put("sku", wp.getSku());
                    productInfo.put("internalName", wp.getInternalName());
                    // Không load description, techSpecsJson, supplier để tăng tốc
                    data.put("warehouseProduct", productInfo);
                }

                return data;
            }).toList();

            return ApiResponse.success("Danh sách tồn kho", stockData);
        } catch (Exception e) {
            log.error("Error getting stocks: ", e);
            return ApiResponse.error("Lỗi khi lấy danh sách tồn kho: " + e.getMessage());
        }
    }

    @Override
    public ApiResponse getStockDetails(Long warehouseProductId) {
        try {
            // 1. Tìm sản phẩm kho
            WarehouseProduct wp = warehouseProductRepository.findById(warehouseProductId)
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm #" + warehouseProductId));

            // 2. Lấy danh sách serial từ bảng ProductDetail
            // Chỉ lấy những cái đang còn trong kho (IN_STOCK) hoặc có thể cả cái đã bán (tùy bạn)
            // Ở đây mình lấy tất cả để bạn dễ quản lý
            List<ProductDetail> details = productDetailRepository.findAllByWarehouseProduct_Id(warehouseProductId);

            // 3. Map sang DTO đơn giản để trả về Frontend
            List<Map<String, Object>> serialList = details.stream().map(d -> {
                Map<String, Object> map = new HashMap<>();
                map.put("serialNumber", d.getSerialNumber());
                map.put("status", d.getStatus()); // IN_STOCK, SOLD, etc.
                map.put("importDate", d.getImportDate());
                map.put("importPrice", d.getImportPrice());
                return map;
            }).toList();

            return ApiResponse.success("Danh sách Serial", serialList);
        } catch (Exception e) {
            return ApiResponse.error("Lỗi lấy chi tiết serial: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ApiResponse exportForSale(SaleExportRequest req) {

        if (req.getItems() == null || req.getItems().isEmpty()) {
            return ApiResponse.error("Danh sách sản phẩm xuất không được để trống");
        }

        // Tạo phiếu xuất
        ExportOrder exportOrder = ExportOrder.builder()
                .exportCode("EX-SALE-" + System.currentTimeMillis())
                .status(ExportStatus.COMPLETED)
                .reason("SALE")
                .note(req.getNote())
                .createdBy(req.getCreatedBy())
                .exportDate(LocalDateTime.now())
                .orderId(req.getOrderId())  // ✅ Link export order với order
                .build();

        List<ExportOrderItem> orderItems = new ArrayList<>();

        for (ExportItemRequest itemReq : req.getItems()) {

            // Tìm warehouseProduct bằng SKU
            WarehouseProduct wp = warehouseProductRepository.findBySku(itemReq.getProductSku())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy SKU: " + itemReq.getProductSku()));

            // Lấy tồn kho
            InventoryStock stock = inventoryStockRepository.findByWarehouseProduct_Id(wp.getId())
                    .orElseThrow(() -> new RuntimeException("Không có tồn kho cho SKU: " + wp.getSku()));

            int exportCount = itemReq.getSerialNumbers().size();

            if (stock.getOnHand() < exportCount) {
                return ApiResponse.error("Không đủ tồn kho cho SKU: " + wp.getSku());
            }

            double totalCost = 0;

            // Xử lý từng serial
            for (String serial : itemReq.getSerialNumbers()) {

                ProductDetail pd = productDetailRepository.findBySerialNumber(serial)
                        .orElseThrow(() -> new RuntimeException("Serial không tồn tại: " + serial));

                if (pd.getStatus() != ProductStatus.IN_STOCK) {
                    return ApiResponse.error("Serial " + serial + " không ở trạng thái IN_STOCK");
                }

                // Cập nhật trạng thái
                pd.setStatus(ProductStatus.SOLD);
                pd.setSoldDate(LocalDateTime.now());
                productDetailRepository.save(pd);

                totalCost += pd.getImportPrice();
            }

            // Cập nhật tồn kho: trừ onHand
            stock.setOnHand(stock.getOnHand() - exportCount);
            
            // ✅ Giải phóng reserved khi xuất kho (hàng đã ra khỏi kho, không cần giữ nữa)
            Long currentReserved = stock.getReserved() != null ? stock.getReserved() : 0L;
            Long newReserved = Math.max(0, currentReserved - exportCount);
            stock.setReserved(newReserved);
            
            inventoryStockRepository.save(stock);

            // Đồng bộ với bảng Product (cả stock và reserved)
            syncStockWithProduct(wp, stock.getOnHand());
            syncReservedWithProduct(wp, newReserved);
            
            log.info("📦 Xuất kho SKU {}: onHand -{}, reserved {} -> {}", 
                wp.getSku(), exportCount, currentReserved, newReserved);

            // Ghi dòng xuất kho
            ExportOrderItem exportItem = ExportOrderItem.builder()
                    .exportOrder(exportOrder)
                    .warehouseProduct(wp)
                    .sku(wp.getSku())
                    .quantity((long) exportCount)
                    .serialNumbers(String.join(",", itemReq.getSerialNumbers()))
                    .totalCost(totalCost)
                    .build();

            orderItems.add(exportItem);
        }

        exportOrder.setItems(orderItems);
        exportOrderRepository.save(exportOrder);

        // ✅ Create GHN order after successful warehouse export
        if (req.getOrderId() != null) {
            try {
                createGHNOrderForExport(req.getOrderId(), exportOrder);
            } catch (Exception e) {
                log.error("Failed to create GHN order for export {}: {}", exportOrder.getExportCode(), e.getMessage());
                // Don't fail the export, just log the error
                // Admin can manually create GHN order later
            }
        }

        return ApiResponse.success("Xuất kho bán hàng thành công", exportOrder.getExportCode());
    }

    private void createGHNOrderForExport(Long orderId, ExportOrder exportOrder) {
        log.info("Creating GHN order for order ID: {}", orderId);

        // Get order details
        com.doan.WEB_TMDT.module.order.entity.Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        // Check if already has GHN order
        if (order.getGhnOrderCode() != null && !order.getGhnOrderCode().isEmpty()) {
            log.warn(" Order {} already has GHN order code: {}", order.getOrderCode(), order.getGhnOrderCode());
            return;
        }

        // Check if need GHN shipping (not free ship)
        if (order.getShippingFee() == 0 || shippingService.isHanoiInnerCity(order.getProvince(), order.getDistrict())) {
            log.info("ℹ Order {} uses internal shipping (no GHN), updating status to READY_TO_SHIP", order.getOrderCode());

            //  Cập nhật status cho đơn nội thành / miễn phí ship
            order.setStatus(com.doan.WEB_TMDT.module.order.entity.OrderStatus.READY_TO_SHIP);
            order.setShippedAt(LocalDateTime.now());
            orderRepository.save(order);

            return;
        }

        // Build full address with ward name (not code) for display
        String wardDisplay = (order.getWardName() != null && !order.getWardName().isEmpty())
                ? order.getWardName()
                : order.getWard(); // Fallback to ward code if name not available
        String fullAddress = String.join(", ",
                order.getAddress(),
                wardDisplay != null ? wardDisplay : "",
                order.getDistrict(),
                order.getProvince()
        ).replaceAll(", ,", ",").trim();

        // Build GHN order request
        com.doan.WEB_TMDT.module.shipping.dto.CreateGHNOrderRequest ghnRequest =
                com.doan.WEB_TMDT.module.shipping.dto.CreateGHNOrderRequest.builder()
                        .toName(order.getCustomer().getFullName())
                        .toPhone(order.getCustomer().getPhone())
                        .toAddress(fullAddress)
                        .toWardCode(order.getWard()) // Ward code from order
                        .toDistrictId(getDistrictIdForGHN(order.getProvince(), order.getDistrict()))
                        .note(order.getNote())
                        .codAmount("COD".equals(order.getPaymentMethod()) ? order.getTotal().intValue() : 0)
                        .weight(1000) // Default 1kg
                        .length(20)
                        .width(20)
                        .height(10)
                        .serviceTypeId(2) // Standard service
                        .paymentTypeId("COD".equals(order.getPaymentMethod()) ? 2 : 1) // 1=Shop trả, 2=Người nhận trả
                        .items(buildGHNItemsFromOrder(order))
                        .build();

        // Call GHN API
        com.doan.WEB_TMDT.module.shipping.dto.CreateGHNOrderResponse ghnResponse =
                shippingService.createGHNOrder(ghnRequest);

        // Update order with GHN info and change status to READY_TO_SHIP
        order.setGhnOrderCode(ghnResponse.getOrderCode());
        order.setGhnShippingStatus("created");
        order.setGhnCreatedAt(LocalDateTime.now());
        order.setGhnExpectedDeliveryTime(ghnResponse.getExpectedDeliveryTime());

        // ✅ Update order status to READY_TO_SHIP (Đã xuất kho, đợi tài xế lấy hàng)
        order.setStatus(com.doan.WEB_TMDT.module.order.entity.OrderStatus.READY_TO_SHIP);
        order.setShippedAt(LocalDateTime.now());

        orderRepository.save(order);

        log.info("✅ GHN order created successfully!");
        log.info("   - Order Code: {}", order.getOrderCode());
        log.info("   - GHN Order Code: {}", ghnResponse.getOrderCode());
        log.info("   - Order Status: {} → SHIPPING", order.getStatus());
        log.info("   - Export Code: {}", exportOrder.getExportCode());
    }

    private Integer getDistrictIdForGHN(String province, String district) {
        // Simple implementation - return default
        // In production, you should call GHN API to get district ID
        return 1485; // Default Hà Đông district
    }

    private List<com.doan.WEB_TMDT.module.shipping.dto.CreateGHNOrderRequest.GHNOrderItem> buildGHNItemsFromOrder(
            com.doan.WEB_TMDT.module.order.entity.Order order) {
        List<com.doan.WEB_TMDT.module.shipping.dto.CreateGHNOrderRequest.GHNOrderItem> items = new ArrayList<>();

        for (com.doan.WEB_TMDT.module.order.entity.OrderItem item : order.getItems()) {
            items.add(com.doan.WEB_TMDT.module.shipping.dto.CreateGHNOrderRequest.GHNOrderItem.builder()
                    .name(item.getProductName())
                    .code(item.getProduct().getSku())
                    .quantity(item.getQuantity())
                    .price(item.getPrice().intValue())
                    .build());
        }

        return items;
    }


    @Override
    @Transactional
    public ApiResponse exportForWarranty(WarrantyExportRequest req) {

        // Lấy item đầu tiên trong request (bảo hành luôn 1 sản phẩm)
        ExportItemRequest itemReq = req.getItems().get(0);

        // Lấy serial đầu tiên
        String serial = itemReq.getSerialNumbers().get(0);

        ProductDetail pd = productDetailRepository.findBySerialNumber(serial)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy serial cần bảo hành: " + serial));

        if (pd.getStatus() != ProductStatus.IN_STOCK &&
                pd.getStatus() != ProductStatus.SOLD) {
            return ApiResponse.error("Serial không thể xuất bảo hành");
        }

        WarehouseProduct wp = pd.getWarehouseProduct();

        InventoryStock stock = inventoryStockRepository
                .findByWarehouseProduct_Id(wp.getId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tồn kho cho sản phẩm"));

        if (stock.getOnHand() <= 0) {
            return ApiResponse.error("Không còn hàng trong kho");
        }

        // Cập nhật trạng thái serial
        pd.setStatus(ProductStatus.WARRANTY);
        productDetailRepository.save(pd);

        // Trừ kho
        stock.setOnHand(stock.getOnHand() - 1);
        inventoryStockRepository.save(stock);

        // Đồng bộ với bảng Product
        syncStockWithProduct(wp, stock.getOnHand());

        // Tạo phiếu xuất
        ExportOrder exportOrder = ExportOrder.builder()
                .exportCode("EX-WARRANTY-" + System.currentTimeMillis())
                .status(ExportStatus.COMPLETED)
                .reason("WARRANTY")
                .note(req.getNote())
                .exportDate(LocalDateTime.now())
                .build();

        exportOrderRepository.save(exportOrder);

        // Ghi dòng chi tiết
        ExportOrderItem item = ExportOrderItem.builder()
                .exportOrder(exportOrder)
                .warehouseProduct(wp)
                .sku(wp.getSku())
                .quantity(1L)
                .serialNumbers(pd.getSerialNumber())
                .totalCost(pd.getImportPrice())
                .build();

        exportOrderItemRepository.save(item);

        return ApiResponse.success("Xuất kho bảo hành thành công", exportOrder.getExportCode());
    }

    /**
     * Helper method: Đồng bộ tồn kho giữa InventoryStock và Product
     * Gọi sau mỗi lần thay đổi tồn kho (nhập/xuất)
     */
    private void syncStockWithProduct(WarehouseProduct wp, Long newOnHand) {
        if (wp.getProduct() != null) {
            Product product = wp.getProduct();
            product.setStockQuantity(newOnHand);
            productRepository.save(product);
            log.info("✅ Đồng bộ tồn kho: {} -> {}", product.getName(), newOnHand);
        }
    }

    /**
     * Helper method: Đồng bộ reserved quantity giữa InventoryStock và Product
     * Gọi sau mỗi lần thay đổi reserved (tạo đơn, hủy đơn)
     */
    private void syncReservedWithProduct(WarehouseProduct wp, Long newReserved) {
        if (wp.getProduct() != null) {
            Product product = wp.getProduct();
            product.setReservedQuantity(newReserved);
            productRepository.save(product);
        }
    }

    /**
     * Public method: Đồng bộ reserved quantity - gọi từ OrderService
     */
    @Override
    @Transactional
    public void syncReservedQuantity(Long warehouseProductId, Long newReserved) {
        WarehouseProduct wp = warehouseProductRepository.findById(warehouseProductId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm kho #" + warehouseProductId));

        // Cập nhật InventoryStock
        InventoryStock stock = inventoryStockRepository.findByWarehouseProduct_Id(warehouseProductId)
                .orElse(InventoryStock.builder()
                        .warehouseProduct(wp)
                        .onHand(0L)
                        .reserved(0L)
                        .damaged(0L)
                        .build());

        stock.setReserved(newReserved);
        inventoryStockRepository.save(stock);

        // Đồng bộ với Product
        syncReservedWithProduct(wp, newReserved);
    }
}