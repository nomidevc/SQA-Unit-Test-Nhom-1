# GIẢI THÍCH CHI TIẾT LUỒNG XỬ LÝ CÁC CHỨC NĂNG
## Từ Frontend → Backend → Database

---

## 📋 MỤC LỤC

1. [Luồng Đặt Hàng (Create Order)](#1-luồng-đặt-hàng)
2. [Luồng Thanh Toán SePay](#2-luồng-thanh-toán-sepay)
3. [Luồng Webhook SePay](#3-luồng-webhook-sepay)
4. [Luồng Xuất Kho](#4-luồng-xuất-kho)
5. [Luồng Shipper Nhận Đơn](#5-luồng-shipper-nhận-đơn)
6. [Luồng Tạo Đơn GHN](#6-luồng-tạo-đơn-ghn)
7. [Luồng Scheduler Hủy Đơn](#7-luồng-scheduler-hủy-đơn)

---

## 1. LUỒNG ĐẶT HÀNG (Create Order)

### 🎯 Mục đích
Khách hàng đặt hàng từ giỏ hàng, hệ thống giữ hàng và tạo đơn hàng

### 📊 Sơ đồ luồng
```
Frontend (checkout page)
    ↓ POST /api/orders/create
Backend (OrderController)
    ↓ orderService.createOrderFromCart()
OrderServiceImpl
    ↓ @Transactional bắt đầu
    ├─ 1. Lấy Customer từ DB
    ├─ 2. Lấy Cart từ DB
    ├─ 3. Lọc CartItem đã chọn
    ├─ 4. LOCK Product (findByIdWithLock)
    ├─ 5. Kiểm tra tồn kho
    ├─ 6. Tính tổng tiền
    ├─ 7. Tạo Order entity
    ├─ 8. Tạo OrderItem entities
    ├─ 9. Tăng reservedQuantity
    ├─ 10. Đồng bộ InventoryStock
    ├─ 11. Save Order
    ├─ 12. Xóa CartItem đã mua
    └─ @Transactional commit
    ↓ Return OrderResponse
Frontend nhận response
    ↓ Redirect đến trang thanh toán
```

### 💻 Chi tiết code flow


#### Bước 1-3: Lấy dữ liệu cơ bản
```java
// OrderServiceImpl.java - Line ~80
@Transactional
public ApiResponse createOrderFromCart(Long customerId, CreateOrderRequest request) {
    // 1. Lấy Customer
    Customer customer = customerRepository.findById(customerId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy khách hàng"));
    
    // 2. Lấy Cart
    Cart cart = cartRepository.findByCustomerId(customerId)
        .orElseThrow(() -> new RuntimeException("Giỏ hàng trống"));
    
    // 3. Lọc CartItem đã chọn
    List<CartItem> selectedCartItems;
    if (request.getSelectedItemIds() != null) {
        selectedCartItems = cart.getItems().stream()
            .filter(item -> request.getSelectedItemIds().contains(item.getId()))
            .collect(Collectors.toList());
    }
```

**Giải thích:**
- `customerRepository.findById()`: Query database bảng `customers` WHERE id = ?
- `cartRepository.findByCustomerId()`: Query bảng `carts` JOIN `cart_items` WHERE customer_id = ?
- `filter()`: Lọc trong memory, chỉ lấy items khách chọn

#### Bước 4-5: Lock và kiểm tra tồn kho (QUAN TRỌNG!)
```java
// Line ~110
for (CartItem cartItem : selectedCartItems) {
    // 4. LOCK Product - Chỉ 1 thread được xử lý tại 1 thời điểm
    Product product = productRepository.findByIdWithLock(cartItem.getProduct().getId())
        .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm"));
    
    // 5. Tính số lượng có thể bán
    Long availableQty = (product.getStockQuantity() != null ? product.getStockQuantity() : 0L)
            - (product.getReservedQuantity() != null ? product.getReservedQuantity() : 0L);
    
    // Kiểm tra đủ hàng không
    if (availableQty < cartItem.getQuantity()) {
        return ApiResponse.error("Sản phẩm " + product.getName() + " chỉ còn " + availableQty);
    }
}
```

**Giải thích `findByIdWithLock()`:**
```java
// ProductRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Optional<Product> findByIdWithLock(@Param("id") Long id);
```

**SQL thực tế:**
```sql
SELECT * FROM products WHERE id = ? FOR UPDATE;
```

**Cơ chế hoạt động:**
- Thread A gọi `findByIdWithLock(1)` → Database lock row id=1
- Thread B cũng gọi `findByIdWithLock(1)` → Phải đợi Thread A xong
- Thread A commit transaction → Release lock
- Thread B mới được xử lý

**Tại sao cần lock?**
```
Không có lock:
- Thread A đọc: stock=10, reserved=0 → available=10 ✓
- Thread B đọc: stock=10, reserved=0 → available=10 ✓
- Thread A đặt 10 cái → reserved=10
- Thread B đặt 10 cái → reserved=20 (SAI! Chỉ có 10 cái)

Có lock:
- Thread A lock → đọc stock=10 → đặt 10 → reserved=10 → unlock
- Thread B lock → đọc stock=10, reserved=10 → available=0 → Báo hết hàng ✓
```


#### Bước 6-7: Tính tiền và tạo Order
```java
// Line ~140
// 6. Tính tổng tiền
Double subtotal = selectedCartItems.stream()
    .mapToDouble(item -> item.getPrice() * item.getQuantity())
    .sum();
Double total = subtotal + request.getShippingFee() - discount;

// 7. Tạo Order
String orderCode = generateOrderCode(); // VD: ORD202501090001
OrderStatus initialStatus = "SEPAY".equals(request.getPaymentMethod()) 
    ? OrderStatus.PENDING_PAYMENT  // Chờ thanh toán
    : OrderStatus.CONFIRMED;        // COD → Tự động xác nhận

Order order = Order.builder()
    .orderCode(orderCode)
    .customer(customer)
    .total(total)
    .status(initialStatus)
    .paymentMethod(request.getPaymentMethod())
    .build();
```

**Giải thích:**
- `generateOrderCode()`: Tạo mã duy nhất dạng ORD + ngày + random
- Nếu COD → `CONFIRMED` ngay (không cần chờ thanh toán)
- Nếu SePay → `PENDING_PAYMENT` (chờ webhook xác nhận)

#### Bước 8-10: Tạo OrderItem và giữ hàng
```java
// Line ~170
List<OrderItem> orderItems = new ArrayList<>();
for (CartItem cartItem : selectedCartItems) {
    Product product = cartItem.getProduct();
    
    // 9. Tăng reservedQuantity (GIỮ HÀNG)
    Long currentReserved = product.getReservedQuantity() != null ? product.getReservedQuantity() : 0L;
    Long newReserved = currentReserved + cartItem.getQuantity();
    product.setReservedQuantity(newReserved);
    
    // 10. Đồng bộ với InventoryStock
    if (product.getWarehouseProduct() != null) {
        inventoryService.syncReservedQuantity(
            product.getWarehouseProduct().getId(), 
            newReserved
        );
    }
    
    // 8. Tạo OrderItem
    OrderItem orderItem = OrderItem.builder()
        .order(order)
        .product(product)
        .quantity(cartItem.getQuantity())
        .reserved(true)   // Đã giữ hàng
        .exported(false)  // Chưa xuất kho
        .build();
    
    orderItems.add(orderItem);
}
order.setItems(orderItems);
```

**Giải thích Reserved Quantity:**
```
Ban đầu:
- stockQuantity = 100 (tổng số trong kho)
- reservedQuantity = 0 (chưa ai giữ)
- sellable = 100 - 0 = 100 (có thể bán)

Sau khi A đặt 10 cái:
- stockQuantity = 100 (chưa xuất kho, vẫn 100)
- reservedQuantity = 10 (đã giữ cho A)
- sellable = 100 - 10 = 90 (người khác chỉ mua được 90)

Sau khi xuất kho cho A:
- stockQuantity = 90 (đã xuất 10 cái)
- reservedQuantity = 0 (đã xuất rồi, không cần giữ nữa)
- sellable = 90 - 0 = 90
```

**Tại sao cần đồng bộ với InventoryStock?**
- `Product` table: Dùng cho module bán hàng (frontend)
- `InventoryStock` table: Dùng cho module kho (warehouse)
- Phải đồng bộ để cả hai module đều biết số lượng chính xác


#### Bước 11-12: Lưu và dọn dẹp
```java
// Line ~210
// 11. Save Order (cascade save OrderItems)
Order savedOrder = orderRepository.save(order);

// 12. Xóa CartItem đã mua
for (CartItem cartItem : selectedCartItems) {
    cart.getItems().remove(cartItem);
}
cartRepository.save(cart);

// Transaction commit tại đây
return ApiResponse.success("Đặt hàng thành công", toOrderResponse(savedOrder));
```

**Giải thích Transaction:**
```java
@Transactional  // Spring tự động bắt đầu transaction
public ApiResponse createOrderFromCart(...) {
    // Tất cả code ở trên
    
    // Nếu có exception → Rollback tất cả
    // Nếu không có exception → Commit tất cả
}
```

**Ví dụ Rollback:**
```
1. Lock product ✓
2. Kiểm tra tồn kho ✓
3. Tạo Order ✓
4. Tăng reservedQuantity ✓
5. Save Order ✓
6. Xóa CartItem → DATABASE ERROR!
   → Spring tự động ROLLBACK
   → Order không được tạo
   → reservedQuantity không tăng
   → CartItem không bị xóa
   → Như chưa làm gì cả!
```

### 🔍 Tổng kết luồng Đặt Hàng

**Input:**
- `customerId`: ID khách hàng
- `CreateOrderRequest`: Địa chỉ, phương thức thanh toán, items đã chọn

**Output:**
- `OrderResponse`: Thông tin đơn hàng vừa tạo

**Database changes:**
- `orders` table: +1 row
- `order_items` table: +N rows (N = số sản phẩm)
- `products` table: `reservedQuantity` tăng
- `inventory_stocks` table: `reserved` tăng
- `cart_items` table: -N rows (xóa items đã mua)

**Đảm bảo:**
- ✅ Không bán quá tồn kho (nhờ lock)
- ✅ Atomic (nhờ @Transactional)
- ✅ Consistent (nhờ đồng bộ Product ↔ InventoryStock)

---

## 2. LUỒNG THANH TOÁN SEPAY

### 🎯 Mục đích
Tạo mã QR thanh toán cho khách hàng chuyển khoản

### 📊 Sơ đồ luồng
```
Frontend (order detail page)
    ↓ POST /api/payments/create
Backend (PaymentController)
    ↓ paymentService.createPayment()
PaymentServiceImpl
    ↓ @Transactional bắt đầu
    ├─ 1. Validate Order
    ├─ 2. Check Payment đã tồn tại chưa
    ├─ 3. Generate payment code (PAY20250109...)
    ├─ 4. Lấy bank account từ DB
    ├─ 5. Generate QR code URL (VietQR)
    ├─ 6. Tạo Payment entity
    ├─ 7. Save Payment
    ├─ 8. Update Order.paymentId
    └─ @Transactional commit
    ↓ Return PaymentResponse (có QR URL)
Frontend hiển thị QR code
```

### 💻 Chi tiết code flow

#### Bước 1-3: Validate và generate code
```java
// PaymentServiceImpl.java - Line ~50
@Transactional
public ApiResponse createPayment(CreateOrderRequest request, Long userId) {
    // 1. Validate Order
    Order order = orderRepository.findById(request.getOrderId())
        .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));
    
    // Verify ownership
    if (!order.getCustomer().getUser().getId().equals(userId)) {
        return ApiResponse.error("Bạn không có quyền thanh toán đơn hàng này");
    }
    
    // 2. Check Payment đã tồn tại chưa
    if (paymentRepository.findByOrderId(order.getId()).isPresent()) {
        return ApiResponse.error("Đơn hàng này đã có thanh toán");
    }
    
    // 3. Generate payment code
    String paymentCode = generatePaymentCode(); // PAY20250109 + random 4 số
}
```


#### Bước 4-5: Lấy bank account và generate QR
```java
// Line ~80
// 4. Lấy bank account từ database
BankAccount bankAccount = bankAccountRepository.findByIsDefaultTrue()
    .orElse(null);

String bankCode = bankAccount != null ? bankAccount.getBankCode() : "VCB";
String accountNumber = bankAccount != null ? bankAccount.getAccountNumber() : "1234567890";
String accountName = bankAccount != null ? bankAccount.getAccountName() : "TECHMART";

// 5. Generate QR code URL
String qrCodeUrl = generateSepayQrCode(paymentCode, request.getAmount(), 
                                       bankCode, accountNumber, accountName);
```

**Giải thích `generateSepayQrCode()`:**
```java
// Line ~300
private String generateSepayQrCode(String content, Double amount, ...) {
    long amountInVnd = Math.round(amount * amountMultiplier);
    
    // VietQR API format
    return String.format(
        "https://img.vietqr.io/image/%s-%s-qr_only.jpg?amount=%d&addInfo=%s&accountName=%s",
        bankCode,           // VCB
        accountNumber,      // 1234567890
        amountInVnd,        // 500000
        content,            // PAY202501090001
        accountName         // TECHMART
    );
}
```

**URL thực tế:**
```
https://img.vietqr.io/image/VCB-1234567890-qr_only.jpg?amount=500000&addInfo=PAY202501090001&accountName=TECHMART
```

**Khi khách quét QR:**
- App ngân hàng tự động điền:
  - Ngân hàng: Vietcombank
  - Số tài khoản: 1234567890
  - Tên: TECHMART
  - Số tiền: 500,000 VND
  - Nội dung: PAY202501090001
- Khách chỉ cần xác nhận chuyển khoản

#### Bước 6-8: Tạo Payment và update Order
```java
// Line ~100
// 6. Tạo Payment entity
Payment payment = Payment.builder()
    .paymentCode(paymentCode)
    .order(order)
    .user(user)
    .amount(request.getAmount())
    .method(PaymentMethod.SEPAY)
    .status(PaymentStatus.PENDING)
    .sepayBankCode(bankCode)
    .sepayAccountNumber(accountNumber)
    .sepayQrCode(qrCodeUrl)
    .expiredAt(LocalDateTime.now().plusMinutes(15))  // Hết hạn sau 15 phút
    .build();

// 7. Save Payment
Payment savedPayment = paymentRepository.save(payment);

// 8. Update Order
order.setPaymentId(savedPayment.getId());
order.setPaymentStatus(com.doan.WEB_TMDT.module.order.entity.PaymentStatus.PENDING);
orderRepository.save(order);

return ApiResponse.success("Tạo thanh toán thành công", toPaymentResponse(savedPayment));
```

**Database changes:**
- `payments` table: +1 row với status = PENDING
- `orders` table: `payment_id` được set, `payment_status` = PENDING

### 🔍 Tổng kết luồng Thanh toán SePay

**Timeline:**
```
T+0s:  Khách click "Thanh toán"
T+1s:  Backend tạo Payment, generate QR
T+2s:  Frontend hiển thị QR code
T+30s: Khách quét QR và chuyển khoản
T+31s: SePay nhận tiền, gọi webhook
T+32s: Backend xử lý webhook, xác nhận đơn
```

---

## 3. LUỒNG WEBHOOK SEPAY

### 🎯 Mục đích
SePay thông báo đã nhận tiền, backend tự động xác nhận đơn hàng

### 📊 Sơ đồ luồng
```
SePay Server
    ↓ POST /api/payments/sepay/webhook
Backend (PaymentController - không cần auth!)
    ↓ paymentService.handleSepayWebhook()
PaymentServiceImpl
    ↓ @Transactional bắt đầu
    ├─ 1. Validate content có chứa "PAY" không
    ├─ 2. Extract payment code từ content
    ├─ 3. Tìm Payment trong DB
    ├─ 4. Verify signature (nếu có)
    ├─ 5. Check Payment đã xử lý chưa
    ├─ 6. Check amount có khớp không
    ├─ 7. Check expired chưa
    ├─ 8. Update Payment → SUCCESS
    ├─ 9. Update Order → CONFIRMED
    ├─ 10. Publish OrderStatusChangedEvent
    └─ @Transactional commit
    ↓ Return success
SePay nhận response 200 OK
```

### 💻 Chi tiết code flow


#### Bước 1-3: Validate và extract payment code
```java
// PaymentServiceImpl.java - Line ~150
@Transactional
public ApiResponse handleSepayWebhook(SepayWebhookRequest request) {
    // 1. Quick validation
    String content = request.getContent();
    if (content == null || !content.contains("PAY")) {
        log.warn("Webhook rejected - content doesn't contain payment code: {}", content);
        return ApiResponse.error("Nội dung không chứa mã thanh toán");
    }
    
    // 2. Extract payment code
    String paymentCode = extractPaymentCode(content);
    // Content: "PAY202501090001 FT2533 Chuyen tien"
    // Extract: "PAY202501090001"
    
    // 3. Tìm Payment
    Payment payment = paymentRepository.findByPaymentCode(paymentCode)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy thanh toán"));
}
```

**Giải thích `extractPaymentCode()`:**
```java
// Line ~400
private String extractPaymentCode(String content) {
    // Tìm vị trí "PAY"
    int index = content.indexOf("PAY");
    if (index != -1) {
        // Lấy từ PAY, tối đa 15 ký tự (PAY + 12 số)
        int endIndex = Math.min(index + 15, content.length());
        String extracted = content.substring(index, endIndex).split("\\s+")[0];
        // "PAY202501090001 FT2533" → split → "PAY202501090001"
        return extracted;
    }
    return content.trim();
}
```

**Tại sao cần extract?**
- Khách có thể ghi thêm: "PAY202501090001 Mua laptop"
- Hoặc bank tự động thêm: "PAY202501090001 FT2533"
- Phải extract ra mã chính xác

#### Bước 4-7: Validate webhook
```java
// Line ~180
// 4. Verify signature (nếu có API token)
BankAccount bankAccount = bankAccountRepository.findByIsDefaultTrue().orElse(null);
if (bankAccount != null && bankAccount.getSepayApiToken() != null) {
    if (!verifySignature(request, bankAccount.getSepayApiToken())) {
        log.error("Invalid signature from SePay webhook");
        return ApiResponse.error("Chữ ký không hợp lệ");
    }
}

// 5. Check đã xử lý chưa (Idempotency)
if (payment.getStatus() == PaymentStatus.SUCCESS) {
    log.warn("Payment already processed: {}", payment.getPaymentCode());
    return ApiResponse.success("Thanh toán đã được xử lý");
}

// 6. Check amount
if (!payment.getAmount().equals(request.getAmount())) {
    log.error("Amount mismatch. Expected: {}, Received: {}", 
              payment.getAmount(), request.getAmount());
    return ApiResponse.error("Số tiền không khớp");
}

// 7. Check expired
if (LocalDateTime.now().isAfter(payment.getExpiredAt())) {
    payment.setStatus(PaymentStatus.EXPIRED);
    paymentRepository.save(payment);
    return ApiResponse.error("Thanh toán đã hết hạn");
}
```

**Giải thích Idempotency:**
```
Tình huống: SePay gọi webhook 2 lần (do network retry)

Lần 1:
- Payment status = PENDING
- Xử lý thành công → status = SUCCESS
- Order → CONFIRMED

Lần 2:
- Payment status = SUCCESS (đã xử lý rồi)
- Return ngay "đã được xử lý"
- Không xử lý lại → Tránh duplicate
```

#### Bước 8-10: Update và publish event
```java
// Line ~220
// 8. Update Payment
payment.setStatus(PaymentStatus.SUCCESS);
payment.setSepayTransactionId(request.getTransactionId());
payment.setPaidAt(LocalDateTime.now());
paymentRepository.save(payment);

// 9. Update Order
Order order = payment.getOrder();
OrderStatus oldStatus = order.getStatus();  // PENDING_PAYMENT

order.setPaymentStatus(com.doan.WEB_TMDT.module.order.entity.PaymentStatus.PAID);
order.setStatus(com.doan.WEB_TMDT.module.order.entity.OrderStatus.CONFIRMED);
order.setConfirmedAt(LocalDateTime.now());
orderRepository.save(order);

// 10. Publish event cho module accounting
OrderStatusChangedEvent event = new OrderStatusChangedEvent(
    this, order, oldStatus, order.getStatus()
);
eventPublisher.publishEvent(event);

return ApiResponse.success("Xử lý thanh toán thành công");
```

**Giải thích Event:**
```java
// AccountingModule.java (module khác)
@EventListener
public void handleOrderStatusChanged(OrderStatusChangedEvent event) {
    Order order = event.getOrder();
    if (order.getStatus() == OrderStatus.CONFIRMED) {
        // Ghi nhận doanh thu
        accountingService.recordRevenue(order);
    }
}
```

**Tại sao dùng Event?**
- Tách biệt module: Payment không cần biết Accounting
- Async: Nếu Accounting lỗi, không ảnh hưởng Payment
- Extensible: Thêm listener mới không cần sửa Payment code

### 🔍 Tổng kết luồng Webhook

**Input từ SePay:**
```json
{
  "transactionId": "FT2533",
  "amount": 500000,
  "content": "PAY202501090001 Chuyen tien",
  "signature": "abc123..."
}
```

**Database changes:**
- `payments` table: status PENDING → SUCCESS, paidAt được set
- `orders` table: status PENDING_PAYMENT → CONFIRMED, confirmedAt được set

**Đảm bảo:**
- ✅ Idempotent (gọi nhiều lần không duplicate)
- ✅ Validated (signature, amount, expiry)
- ✅ Atomic (transaction)


---

## 4. LUỒNG XUẤT KHO

### 🎯 Mục đích
Nhân viên kho xuất hàng cho đơn đã xác nhận, gán serial number

### 📊 Sơ đồ luồng
```
Frontend (warehouse order detail)
    ↓ POST /api/inventory/sale-export
Backend (InventoryController)
    ↓ inventoryService.createSaleExport()
InventoryServiceImpl
    ↓ @Transactional bắt đầu
    ├─ 1. Validate Order (phải CONFIRMED)
    ├─ 2. Tạo SaleExportOrder
    ├─ 3. Loop qua từng OrderItem:
    │   ├─ 3a. Validate serial number
    │   ├─ 3b. Tạo SaleExportItem
    │   ├─ 3c. Gán serial cho OrderItem
    │   ├─ 3d. Update SerialNumber status → SOLD
    │   ├─ 3e. Trừ InventoryStock.onHand
    │   ├─ 3f. Trừ InventoryStock.reserved
    │   ├─ 3g. Trừ Product.stockQuantity
    │   └─ 3h. Trừ Product.reservedQuantity
    ├─ 4. Update Order → READY_TO_SHIP
    ├─ 5. Save tất cả
    └─ @Transactional commit
    ↓ Return success
Frontend hiển thị "Xuất kho thành công"
```

### 💻 Chi tiết code flow

#### Bước 1-2: Validate và tạo SaleExportOrder
```java
// InventoryServiceImpl.java - Line ~200
@Transactional
public ApiResponse createSaleExport(SaleExportRequest request) {
    // 1. Validate Order
    Order order = orderRepository.findById(request.getOrderId())
        .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));
    
    if (order.getStatus() != OrderStatus.CONFIRMED) {
        return ApiResponse.error("Chỉ xuất kho cho đơn đã xác nhận");
    }
    
    // 2. Tạo SaleExportOrder
    SaleExportOrder exportOrder = SaleExportOrder.builder()
        .order(order)
        .exportDate(LocalDateTime.now())
        .exportedBy(employeeRepository.findById(request.getEmployeeId()).orElse(null))
        .note(request.getNote())
        .build();
    
    exportOrder = saleExportOrderRepository.save(exportOrder);
}
```

#### Bước 3: Loop qua OrderItem và xử lý (QUAN TRỌNG!)
```java
// Line ~230
List<SaleExportItem> exportItems = new ArrayList<>();

for (OrderItem orderItem : order.getItems()) {
    Product product = orderItem.getProduct();
    WarehouseProduct warehouseProduct = product.getWarehouseProduct();
    
    // 3a. Validate serial number (nếu có)
    String serialNumber = request.getSerialNumbers().get(orderItem.getId());
    if (serialNumber != null) {
        SerialNumber serial = serialNumberRepository.findBySerialNumber(serialNumber)
            .orElseThrow(() -> new RuntimeException("Serial không tồn tại: " + serialNumber));
        
        if (serial.getStatus() != SerialStatus.AVAILABLE) {
            return ApiResponse.error("Serial " + serialNumber + " không khả dụng");
        }
        
        // 3c. Gán serial cho OrderItem
        orderItem.setSerialNumber(serialNumber);
        
        // 3d. Update SerialNumber status
        serial.setStatus(SerialStatus.SOLD);
        serial.setSoldAt(LocalDateTime.now());
        serialNumberRepository.save(serial);
    }
    
    // 3b. Tạo SaleExportItem
    SaleExportItem exportItem = SaleExportItem.builder()
        .saleExportOrder(exportOrder)
        .warehouseProduct(warehouseProduct)
        .quantity(orderItem.getQuantity())
        .serialNumber(serialNumber)
        .build();
    
    exportItems.add(exportItem);
    saleExportItemRepository.save(exportItem);
```

**Giải thích Serial Number:**
```
Trước xuất kho:
- SerialNumber: "LT-20250109-0001"
- Status: AVAILABLE
- OrderItem.serialNumber: null

Sau xuất kho:
- SerialNumber: "LT-20250109-0001"
- Status: SOLD
- SoldAt: 2025-01-09 10:30:00
- OrderItem.serialNumber: "LT-20250109-0001"
```


#### Bước 3e-3h: Trừ tồn kho (QUAN TRỌNG!)
```java
    // 3e-3f. Trừ InventoryStock
    InventoryStock stock = inventoryStockRepository
        .findByWarehouseProduct_Id(warehouseProduct.getId())
        .orElseThrow(() -> new RuntimeException("Không tìm thấy tồn kho"));
    
    // Trừ onHand (tổng số trong kho)
    Long newOnHand = stock.getOnHand() - orderItem.getQuantity();
    stock.setOnHand(newOnHand);
    
    // Trừ reserved (số đã giữ)
    Long newReserved = stock.getReserved() - orderItem.getQuantity();
    stock.setReserved(Math.max(0, newReserved));
    
    // Tính lại sellable
    stock.setSellable(stock.getOnHand() - stock.getReserved() - stock.getDamaged());
    inventoryStockRepository.save(stock);
    
    // 3g-3h. Trừ Product
    Long newStockQty = product.getStockQuantity() - orderItem.getQuantity();
    product.setStockQuantity(newStockQty);
    
    Long newReservedQty = product.getReservedQuantity() - orderItem.getQuantity();
    product.setReservedQuantity(Math.max(0, newReservedQty));
    
    productRepository.save(product);
    
    // Đánh dấu OrderItem đã xuất
    orderItem.setExported(true);
    orderItemRepository.save(orderItem);
}
```

**Giải thích chi tiết:**
```
Ban đầu (sau khi đặt hàng):
InventoryStock:
- onHand = 100 (tổng trong kho)
- reserved = 10 (đã giữ cho đơn hàng)
- damaged = 0
- sellable = 100 - 10 - 0 = 90

Product:
- stockQuantity = 100
- reservedQuantity = 10

Sau khi xuất kho 10 cái:
InventoryStock:
- onHand = 90 (đã xuất 10)
- reserved = 0 (không cần giữ nữa)
- damaged = 0
- sellable = 90 - 0 - 0 = 90

Product:
- stockQuantity = 90
- reservedQuantity = 0

OrderItem:
- exported = true
- serialNumber = "LT-20250109-0001"
```

**Tại sao phải trừ cả onHand và reserved?**
- `onHand`: Hàng thực sự ra khỏi kho → Phải trừ
- `reserved`: Hàng đã xuất rồi, không cần giữ nữa → Phải trừ
- Nếu chỉ trừ onHand mà không trừ reserved → sellable sẽ âm!

#### Bước 4-5: Update Order và save
```java
// Line ~300
// 4. Update Order status
order.setStatus(OrderStatus.READY_TO_SHIP);
orderRepository.save(order);

// 5. Transaction commit
return ApiResponse.success("Xuất kho thành công", exportOrder);
```

### 🔍 Tổng kết luồng Xuất Kho

**Input:**
```json
{
  "orderId": 123,
  "employeeId": 5,
  "serialNumbers": {
    "orderItemId_1": "LT-20250109-0001",
    "orderItemId_2": "LT-20250109-0002"
  },
  "note": "Xuất kho cho đơn ORD202501090001"
}
```

**Database changes:**
- `sale_export_orders` table: +1 row
- `sale_export_items` table: +N rows
- `serial_numbers` table: status AVAILABLE → SOLD
- `order_items` table: serialNumber được set, exported = true
- `inventory_stocks` table: onHand giảm, reserved giảm, sellable tính lại
- `products` table: stockQuantity giảm, reservedQuantity giảm
- `orders` table: status CONFIRMED → READY_TO_SHIP

**Đảm bảo:**
- ✅ Serial không bị trùng (unique constraint)
- ✅ Tồn kho chính xác (trừ cả onHand và reserved)
- ✅ Atomic (transaction)
- ✅ Đồng bộ Product ↔ InventoryStock

---

## 5. LUỒNG SHIPPER NHẬN ĐƠN

### 🎯 Mục đích
Shipper nội bộ nhận đơn hàng trong nội thành Hà Nội

### 📊 Sơ đồ luồng
```
Frontend (shipper app)
    ↓ GET /api/shipper/available-orders
Backend lọc đơn READY_TO_SHIP + nội thành HN
    ↓ Return danh sách đơn
Shipper chọn đơn
    ↓ POST /api/shipper/claim/{orderId}
Backend (ShipperAssignmentController)
    ↓ shipperService.claimOrder()
ShipperAssignmentServiceImpl
    ↓ @Transactional bắt đầu
    ├─ 1. Validate Order (READY_TO_SHIP, no GHN, nội thành)
    ├─ 2. Check đã có shipper chưa (race condition)
    ├─ 3. Validate Shipper
    ├─ 4. Tạo ShipperAssignment (DELIVERING)
    ├─ 5. Update Order → SHIPPING
    └─ @Transactional commit
    ↓ Return success
Frontend hiển thị "Đã nhận đơn"
```


### 💻 Chi tiết code flow

#### Bước 1: Validate Order
```java
// ShipperAssignmentServiceImpl.java - Line ~50
@Transactional
public ApiResponse claimOrder(Long orderId, Long shipperId) {
    // 1. Validate Order
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));
    
    // Phải READY_TO_SHIP
    if (order.getStatus() != OrderStatus.READY_TO_SHIP) {
        return ApiResponse.error("Chỉ có thể nhận đơn hàng ở trạng thái 'Đã chuẩn bị hàng'");
    }
    
    // Không có GHN order code
    if (order.getGhnOrderCode() != null && !order.getGhnOrderCode().isEmpty()) {
        return ApiResponse.error("Đơn hàng này đã sử dụng Giao Hàng Nhanh");
    }
    
    // Phải trong nội thành Hà Nội
    if (!isHanoiInnerCity(order)) {
        return ApiResponse.error("Chỉ có thể nhận đơn hàng trong nội thành Hà Nội");
    }
}
```

**Giải thích `isHanoiInnerCity()`:**
```java
// Line ~200
private boolean isHanoiInnerCity(Order order) {
    String province = order.getProvince().toLowerCase().trim();
    String district = order.getDistrict().toLowerCase().trim();
    
    // Kiểm tra có phải Hà Nội không
    boolean isHanoi = province.contains("hà nội") || 
                     province.contains("ha noi") || 
                     province.equals("hanoi");
    
    if (!isHanoi) return false;
    
    // Danh sách 12 quận nội thành
    String[] innerDistricts = {
        "ba đình", "hoàn kiếm", "tây hồ", "long biên",
        "cầu giấy", "đống đa", "hai bà trưng", "hoàng mai",
        "thanh xuân", "nam từ liêm", "bắc từ liêm", "hà đông"
    };
    
    // Kiểm tra district có trong danh sách không
    for (String innerDistrict : innerDistricts) {
        if (district.contains(innerDistrict)) {
            return true;
        }
    }
    
    return false;
}
```

#### Bước 2-3: Check race condition và validate shipper
```java
// Line ~80
// 2. Check đã có shipper nhận chưa (QUAN TRỌNG!)
if (assignmentRepository.existsByOrderId(order.getId())) {
    return ApiResponse.error("Đơn hàng này đã có shipper khác nhận rồi");
}

// 3. Validate Shipper
Employee shipper = employeeRepository.findById(shipperId)
    .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên"));

if (shipper.getPosition() != Position.SHIPPER) {
    return ApiResponse.error("Bạn không phải là shipper");
}
```

**Xử lý Race Condition:**
```java
// ShipperAssignment entity có unique constraint
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"order_id"})
})
public class ShipperAssignment { ... }
```

**Tình huống:**
```
T+0ms: Shipper A gọi claimOrder(orderId=123)
T+0ms: Shipper B gọi claimOrder(orderId=123)

Thread A:
- Check existsByOrderId(123) → false ✓
- Tạo ShipperAssignment(order=123, shipper=A)
- Save → SUCCESS

Thread B:
- Check existsByOrderId(123) → false ✓ (vì A chưa commit)
- Tạo ShipperAssignment(order=123, shipper=B)
- Save → DataIntegrityViolationException! (unique constraint)
- Catch exception → Return "đơn đã có người nhận"
```

#### Bước 4-5: Tạo Assignment và update Order
```java
// Line ~100
try {
    // 4. Tạo ShipperAssignment
    LocalDateTime now = LocalDateTime.now();
    ShipperAssignment assignment = ShipperAssignment.builder()
        .order(order)
        .shipper(shipper)
        .status(ShipperAssignmentStatus.DELIVERING)  // Đang giao luôn
        .assignedAt(now)
        .claimedAt(now)
        .deliveringAt(now)
        .build();
    
    assignmentRepository.save(assignment);
    
    // 5. Update Order
    order.setStatus(OrderStatus.SHIPPING);
    order.setShippedAt(now);
    orderRepository.save(order);
    
    return ApiResponse.success("Đã nhận đơn và bắt đầu giao hàng", toResponse(assignment));
    
} catch (DataIntegrityViolationException e) {
    // Race condition: 2 shipper claim cùng lúc
    return ApiResponse.error("Đơn hàng này đã có shipper khác nhận rồi. Vui lòng chọn đơn khác.");
}
```

### 🔍 Tổng kết luồng Shipper Nhận Đơn

**Database changes:**
- `shipper_assignments` table: +1 row với status = DELIVERING
- `orders` table: status READY_TO_SHIP → SHIPPING, shippedAt được set

**Đảm bảo:**
- ✅ Chỉ shipper nội thành mới nhận được
- ✅ Không bị duplicate (unique constraint)
- ✅ Race condition safe (catch exception)

---

## 6. LUỒNG TẠO ĐƠN GHN

### 🎯 Mục đích
Tạo đơn vận chuyển qua Giao Hàng Nhanh cho đơn hàng ngoại thành

### 📊 Sơ đồ luồng
```
Frontend (warehouse order detail)
    ↓ POST /api/shipping/ghn/create-order
Backend (ShippingController)
    ↓ shippingService.createGHNOrder()
ShippingServiceImpl
    ├─ 1. Validate Order
    ├─ 2. Build CreateGHNOrderRequest
    ├─ 3. Call GHN API (HTTP POST)
    ├─ 4. Parse GHNOrderResponse
    ├─ 5. Update Order với GHN info
    └─ Return success
    ↓ Frontend hiển thị mã vận đơn
```


### 💻 Chi tiết code flow

#### Bước 1-2: Validate và build request
```java
// ShippingServiceImpl.java - Line ~100
public GHNOrderResponse createGHNOrder(CreateGHNOrderRequest request) {
    // 1. Validate Order
    Order order = orderRepository.findById(request.getOrderId())
        .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));
    
    if (order.getStatus() != OrderStatus.READY_TO_SHIP) {
        throw new RuntimeException("Đơn hàng chưa sẵn sàng giao");
    }
    
    // 2. Build GHN request
    Map<String, Object> ghnRequest = new HashMap<>();
    ghnRequest.put("to_name", order.getCustomer().getFullName());
    ghnRequest.put("to_phone", order.getCustomer().getPhone());
    ghnRequest.put("to_address", order.getAddress());
    ghnRequest.put("to_ward_code", order.getWard());
    ghnRequest.put("to_district_id", getGHNDistrictId(order.getDistrict()));
    
    // Items
    List<Map<String, Object>> items = new ArrayList<>();
    for (OrderItem item : order.getItems()) {
        Map<String, Object> ghnItem = new HashMap<>();
        ghnItem.put("name", item.getProductName());
        ghnItem.put("quantity", item.getQuantity());
        ghnItem.put("price", item.getPrice().intValue());
        items.add(ghnItem);
    }
    ghnRequest.put("items", items);
    
    // Service & payment
    ghnRequest.put("service_type_id", 2); // Standard
    ghnRequest.put("payment_type_id", order.getPaymentMethod().equals("COD") ? 2 : 1);
    ghnRequest.put("required_note", "CHOXEMHANGKHONGTHU"); // Cho xem hàng không thử
    ghnRequest.put("weight", calculateTotalWeight(order));
}
```

#### Bước 3-4: Call GHN API
```java
// Line ~150
// 3. Call GHN API
HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
headers.set("Token", ghnApiToken);  // From application.properties
headers.set("ShopId", ghnShopId);

HttpEntity<Map<String, Object>> entity = new HttpEntity<>(ghnRequest, headers);

try {
    ResponseEntity<Map> response = restTemplate.postForEntity(
        ghnApiUrl + "/v2/shipping-order/create",
        entity,
        Map.class
    );
    
    // 4. Parse response
    Map<String, Object> responseBody = response.getBody();
    Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
    
    String orderCode = (String) data.get("order_code");
    String expectedDeliveryTime = (String) data.get("expected_delivery_time");
    Integer totalFee = (Integer) data.get("total_fee");
    
    GHNOrderResponse ghnResponse = GHNOrderResponse.builder()
        .orderCode(orderCode)
        .expectedDeliveryTime(expectedDeliveryTime)
        .totalFee(totalFee.doubleValue())
        .build();
    
} catch (Exception e) {
    log.error("Error calling GHN API: {}", e.getMessage());
    throw new RuntimeException("Không thể tạo đơn GHN: " + e.getMessage());
}
```

**GHN API Request thực tế:**
```json
POST https://dev-online-gateway.ghn.vn/shiip/public-api/v2/shipping-order/create
Headers:
  Token: your-ghn-token
  ShopId: your-shop-id
  Content-Type: application/json

Body:
{
  "to_name": "Nguyễn Văn A",
  "to_phone": "0987654321",
  "to_address": "123 Đường ABC",
  "to_ward_code": "20308",
  "to_district_id": 1542,
  "items": [
    {
      "name": "Laptop Dell XPS 13",
      "quantity": 1,
      "price": 25000000
    }
  ],
  "service_type_id": 2,
  "payment_type_id": 2,
  "required_note": "CHOXEMHANGKHONGTHU",
  "weight": 2000
}
```

**GHN API Response:**
```json
{
  "code": 200,
  "message": "Success",
  "data": {
    "order_code": "GHN123456",
    "expected_delivery_time": "2025-01-12T17:00:00",
    "total_fee": 35000
  }
}
```

#### Bước 5: Update Order
```java
// Line ~200
// 5. Update Order với GHN info
order.setGhnOrderCode(ghnResponse.getOrderCode());
order.setGhnCreatedAt(LocalDateTime.now());
order.setGhnExpectedDeliveryTime(
    LocalDateTime.parse(ghnResponse.getExpectedDeliveryTime())
);
order.setStatus(OrderStatus.SHIPPING);
order.setShippedAt(LocalDateTime.now());
orderRepository.save(order);

return ghnResponse;
```

### 🔍 Tổng kết luồng Tạo Đơn GHN

**Database changes:**
- `orders` table: 
  - ghnOrderCode = "GHN123456"
  - ghnCreatedAt = now
  - ghnExpectedDeliveryTime = "2025-01-12 17:00:00"
  - status READY_TO_SHIP → SHIPPING

**External API call:**
- POST to GHN API
- Timeout: 30 seconds
- Retry: 3 lần nếu network error

**Error handling:**
```java
try {
    // Call GHN API
} catch (HttpClientErrorException e) {
    // 4xx error: Invalid request
    throw new RuntimeException("Thông tin đơn hàng không hợp lệ");
} catch (HttpServerErrorException e) {
    // 5xx error: GHN server error
    throw new RuntimeException("GHN đang bảo trì, vui lòng thử lại sau");
} catch (ResourceAccessException e) {
    // Network timeout
    throw new RuntimeException("Không thể kết nối GHN, vui lòng kiểm tra mạng");
}
```

---

## 7. LUỒNG SCHEDULER HỦY ĐƠN

### 🎯 Mục đích
Tự động hủy các đơn hàng quá hạn 15 phút chưa thanh toán

### 📊 Sơ đồ luồng
```
Spring Scheduler (mỗi 5 phút)
    ↓ @Scheduled trigger
PaymentScheduler.expireOldPayments()
    ↓ Query Payment PENDING + expired
PaymentServiceImpl.expireOldPayments()
    ↓ Loop qua từng Payment
    ├─ 1. Update Payment → EXPIRED
    ├─ 2. Gọi orderService.cancelOrderByCustomer()
    │   ├─ 2a. Giảm reservedQuantity
    │   ├─ 2b. Đồng bộ InventoryStock
    │   ├─ 2c. Xóa Order (nếu PENDING_PAYMENT)
    │   └─ 2d. Hoặc chuyển → CANCELLED
    └─ Log kết quả
```

### 💻 Chi tiết code flow


#### Scheduler configuration
```java
// PaymentScheduler.java
@Component
public class PaymentScheduler {
    
    private final PaymentService paymentService;
    
    // Chạy mỗi 5 phút (300,000 milliseconds)
    @Scheduled(fixedRate = 300000)
    public void expireOldPayments() {
        log.info("Running payment expiration scheduler...");
        paymentService.expireOldPayments();
    }
}
```

**Giải thích @Scheduled:**
- `fixedRate = 300000`: Chạy mỗi 5 phút
- Chạy ngay khi app start, sau đó lặp lại mỗi 5 phút
- Chạy trong background thread, không block main thread

#### Bước 1: Query và update Payment
```java
// PaymentServiceImpl.java - Line ~350
@Transactional
public void expireOldPayments() {
    LocalDateTime now = LocalDateTime.now();
    
    // Query các payment PENDING đã quá hạn
    List<Payment> expiredPayments = paymentRepository
        .findByStatusAndExpiredAtBefore(PaymentStatus.PENDING, now);
    
    log.info("Found {} expired payments to process", expiredPayments.size());
    
    for (Payment payment : expiredPayments) {
        // 1. Update Payment → EXPIRED
        payment.setStatus(PaymentStatus.EXPIRED);
        payment.setFailureReason("Không thanh toán trong thời gian quy định");
        paymentRepository.save(payment);
```

**SQL thực tế:**
```sql
SELECT * FROM payments 
WHERE status = 'PENDING' 
  AND expired_at < '2025-01-09 10:30:00';
```

**Ví dụ:**
```
Hiện tại: 10:30:00

Payment 1:
- Created: 10:00:00
- ExpiredAt: 10:15:00 (< 10:30:00) → EXPIRED ✓

Payment 2:
- Created: 10:20:00
- ExpiredAt: 10:35:00 (> 10:30:00) → Chưa expired
```

#### Bước 2: Hủy Order
```java
        // 2. Gọi hàm hủy đơn
        Order order = payment.getOrder();
        if (order != null && order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            try {
                Long customerId = order.getCustomer().getId();
                orderService.cancelOrderByCustomer(
                    order.getId(), 
                    customerId, 
                    "Không thanh toán trong thời gian quy định"
                );
                log.info("Cancelled order {} due to payment expiration", order.getOrderCode());
            } catch (Exception e) {
                log.error("Failed to cancel order {}: {}", 
                    order.getOrderCode(), e.getMessage());
            }
        }
    }
}
```

**Chi tiết `cancelOrderByCustomer()`:**
```java
// OrderServiceImpl.java - Line ~400
@Transactional
public ApiResponse cancelOrderByCustomer(Long orderId, Long customerId, String reason) {
    Order order = orderRepository.findById(orderId)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));
    
    // Nếu PENDING_PAYMENT → XÓA KHỎI DB
    if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
        // 2a. Giải phóng reserved quantity
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            Long currentReserved = product.getReservedQuantity();
            Long newReserved = Math.max(0, currentReserved - item.getQuantity());
            product.setReservedQuantity(newReserved);
            
            // 2b. Đồng bộ InventoryStock
            if (product.getWarehouseProduct() != null) {
                inventoryService.syncReservedQuantity(
                    product.getWarehouseProduct().getId(), 
                    newReserved
                );
            }
        }
        
        // 2c. Xóa Payment trước (foreign key)
        paymentRepository.findByOrderId(order.getId())
            .ifPresent(p -> paymentRepository.delete(p));
        
        // Xóa Order
        orderRepository.delete(order);
        
        return ApiResponse.success("Đã hủy đơn hàng");
    }
    
    // Nếu đã CONFIRMED trở đi → Chuyển sang CANCELLED (lưu lại)
    // ... (code xử lý CANCELLED)
}
```

**Giải thích tại sao xóa vs chuyển CANCELLED:**
```
PENDING_PAYMENT (chưa thanh toán):
- Chưa có ý nghĩa kinh doanh
- Xóa để giữ DB sạch
- Giải phóng reserved quantity

CONFIRMED trở đi (đã thanh toán hoặc COD):
- Đã có ý nghĩa kinh doanh
- Cần lưu lại để báo cáo, thống kê
- Chuyển sang CANCELLED, lưu lý do
```

### 🔍 Tổng kết luồng Scheduler

**Chạy mỗi 5 phút:**
```
10:00 → Check payments expired before 10:00
10:05 → Check payments expired before 10:05
10:10 → Check payments expired before 10:10
...
```

**Database changes (mỗi lần chạy):**
- `payments` table: N rows status PENDING → EXPIRED
- `orders` table: N rows bị xóa (nếu PENDING_PAYMENT)
- `products` table: reservedQuantity giảm
- `inventory_stocks` table: reserved giảm

**Log output:**
```
2025-01-09 10:30:00 INFO  Running payment expiration scheduler...
2025-01-09 10:30:01 INFO  Found 3 expired payments to process
2025-01-09 10:30:02 INFO  Cancelled order ORD202501090001 due to payment expiration
2025-01-09 10:30:03 INFO  Cancelled order ORD202501090002 due to payment expiration
2025-01-09 10:30:04 INFO  Cancelled order ORD202501090003 due to payment expiration
2025-01-09 10:30:05 INFO  Expired 3 old payments and cancelled their orders
```

---

## 📚 TỔNG KẾT TẤT CẢ LUỒNG

### So sánh các luồng

| Luồng | Trigger | Transaction | External API | Database Tables |
|-------|---------|-------------|--------------|-----------------|
| Đặt hàng | User action | ✅ | ❌ | orders, order_items, products, inventory_stocks, cart_items |
| Thanh toán SePay | User action | ✅ | ❌ (chỉ generate URL) | payments, orders |
| Webhook SePay | External call | ✅ | ❌ | payments, orders |
| Xuất kho | Staff action | ✅ | ❌ | sale_export_orders, sale_export_items, serial_numbers, order_items, inventory_stocks, products, orders |
| Shipper nhận đơn | Staff action | ✅ | ❌ | shipper_assignments, orders |
| Tạo đơn GHN | Staff action | ❌ | ✅ (GHN API) | orders |
| Scheduler hủy đơn | Scheduled | ✅ | ❌ | payments, orders, products, inventory_stocks |

### Key Concepts đã học

1. **Pessimistic Locking**: `findByIdWithLock()` với `FOR UPDATE`
2. **Transaction**: `@Transactional` đảm bảo ACID
3. **Idempotency**: Check status trước khi xử lý
4. **Race Condition**: Unique constraint + catch exception
5. **Reserved Quantity**: Giữ hàng khi đặt, trả lại khi hủy
6. **Webhook**: External service gọi vào để notify
7. **Scheduler**: Background job chạy định kỳ
8. **Event-Driven**: Publish event để tách biệt module
9. **External API**: Call GHN API với error handling
10. **Saga Pattern**: Compensation khi distributed transaction fail

---

## 🎓 CÂU HỎI TỰ KIỂM TRA

1. Tại sao cần lock Product khi đặt hàng?
2. Reserved quantity khác gì với stock quantity?
3. Webhook có thể bị gọi 2 lần, xử lý thế nào?
4. Tại sao xóa Order PENDING_PAYMENT nhưng giữ lại CANCELLED?
5. Race condition khi 2 shipper claim cùng đơn, xử lý ra sao?
6. Nếu GHN API fail, có rollback xuất kho không?
7. Scheduler chạy mỗi 5 phút, tại sao không 1 phút?
8. Transaction commit khi nào? Rollback khi nào?
9. Tại sao phải đồng bộ Product và InventoryStock?
10. Event-Driven có ưu điểm gì so với gọi trực tiếp?

---

**Chúc bạn hiểu rõ các luồng xử lý! 🚀**
