# CÂU HỎI PHẢN BIỆN KHÓ VÀ TRẢ LỜI CHI TIẾT
## Đồ án: Website Thương Mại Điện Tử (WEB_TMDT)

---

# PHẦN 1: CÂU HỎI VỀ LỖ HỔNG BẢO MẬT

---

## Câu 1.1: Tôi thấy endpoint `/api/payment/test-webhook/{paymentCode}` cho phép bất kỳ ai cũng có thể giả mạo thanh toán thành công. Đây là lỗ hổng nghiêm trọng, giải thích?

**Phân tích vấn đề:**

```java
// PaymentController.java - KHÔNG CÓ @PreAuthorize
@RequestMapping(value = "/test-webhook/{paymentCode}", method = {RequestMethod.GET, RequestMethod.POST})
public ApiResponse testWebhook(@PathVariable String paymentCode) {
    // Tạo mock webhook và xử lý như thật
    SepayWebhookRequest mockRequest = new SepayWebhookRequest();
    mockRequest.setContent(paymentCode);
    mockRequest.setStatus("SUCCESS");
    return paymentService.handleSepayWebhook(mockRequest);
}
```

**Hậu quả:**
- Kẻ tấn công có thể đặt hàng → Lấy paymentCode → Gọi `/api/payment/test-webhook/PAYxxx` → Đơn hàng được xác nhận mà KHÔNG CẦN THANH TOÁN

**Trả lời:**

Đây là endpoint **CHỈ DÙNG CHO DEVELOPMENT/TESTING**, trong production cần:

1. **Xóa hoàn toàn endpoint này** hoặc
2. **Thêm điều kiện kiểm tra môi trường:**
```java
@Value("${spring.profiles.active:prod}")
private String activeProfile;

@RequestMapping(value = "/test-webhook/{paymentCode}")
public ApiResponse testWebhook(@PathVariable String paymentCode) {
    if (!"dev".equals(activeProfile) && !"test".equals(activeProfile)) {
        return ApiResponse.error("Endpoint này chỉ khả dụng trong môi trường development");
    }
    // ... logic test
}
```

3. **Hoặc bảo vệ bằng API key riêng:**
```java
@PostMapping("/test-webhook/{paymentCode}")
public ApiResponse testWebhook(
    @PathVariable String paymentCode,
    @RequestHeader("X-Test-Api-Key") String apiKey) {
    if (!testApiKey.equals(apiKey)) {
        return ApiResponse.error("Unauthorized");
    }
}
```

---

## Câu 1.2: Signature verification trong webhook SePay đang return true mọi lúc. Điều này có nghĩa gì?

**Code hiện tại:**
```java
// PaymentServiceImpl.java
private boolean verifySignature(SepayWebhookRequest request, String apiToken) {
    // TODO: Implement real signature verification
    log.info("Verifying signature...");
    return true; // LUÔN TRẢ VỀ TRUE!
}
```

**Hậu quả:**
- Bất kỳ ai biết format webhook đều có thể gửi request giả mạo
- Không có cách nào xác minh request thực sự đến từ SePay

**Trả lời:**

Cần implement signature verification theo tài liệu SePay:

```java
private boolean verifySignature(SepayWebhookRequest request, String apiToken) {
    // Tạo chuỗi data theo thứ tự quy định của SePay
    String data = request.getTransactionId() + "|" + 
                  request.getAmount() + "|" + 
                  request.getContent() + "|" + 
                  apiToken;
    
    // Hash SHA256
    String calculatedSignature = DigestUtils.sha256Hex(data);
    
    // So sánh với signature từ request
    return calculatedSignature.equals(request.getSignature());
}
```

**Giải pháp tạm thời trong đồ án:**
- Whitelist IP của SePay server
- Kiểm tra paymentCode có tồn tại và đang PENDING
- Log tất cả webhook requests để audit

---

## Câu 1.3: ShipperAssignmentController cho phép shipper truyền `shipperId` từ request. Điều này có vấn đề gì?

**Code hiện tại:**
```java
@PostMapping("/claim")
@PreAuthorize("hasAuthority('SHIPPER')")
public ApiResponse claimOrder(
    @RequestParam Long orderId,
    @RequestParam Long shipperId) {  // SHIPPER TỰ TRUYỀN ID!
    return shipperAssignmentService.claimOrder(orderId, shipperId);
}
```

**Vấn đề:**
- Shipper A có thể truyền `shipperId` của Shipper B
- Shipper có thể nhận đơn thay người khác
- Không có validation shipperId khớp với user đang đăng nhập

**Trả lời:**

Đây là lỗi **Insecure Direct Object Reference (IDOR)**. Cần sửa:

```java
@PostMapping("/claim")
@PreAuthorize("hasAuthority('SHIPPER')")
public ApiResponse claimOrder(
    @RequestParam Long orderId,
    Authentication authentication) {
    
    // Lấy shipperId từ token, KHÔNG từ request
    Long shipperId = getShipperIdFromAuth(authentication);
    return shipperAssignmentService.claimOrder(orderId, shipperId);
}

private Long getShipperIdFromAuth(Authentication auth) {
    String email = auth.getName();
    Employee employee = employeeRepository.findByUserEmail(email)
        .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên"));
    
    if (employee.getPosition() != Position.SHIPPER) {
        throw new RuntimeException("Bạn không phải shipper");
    }
    return employee.getId();
}
```

---

# PHẦN 2: CÂU HỎI VỀ RACE CONDITION VÀ CONCURRENCY

---

## Câu 2.1: Khi 2 shipper cùng nhận 1 đơn hàng đồng thời, hệ thống xử lý thế nào? Có race condition không?

**Code hiện tại:**
```java
@Transactional
public ApiResponse claimOrder(Long orderId, Long shipperId) {
    // Kiểm tra đã có shipper nhận chưa
    if (assignmentRepository.existsByOrderId(order.getId())) {
        return ApiResponse.error("Đơn hàng này đã có shipper nhận");
    }
    
    // Tạo assignment
    ShipperAssignment assignment = ShipperAssignment.builder()...
    assignmentRepository.save(assignment);
}
```

**Vấn đề:**
- Giữa lúc `existsByOrderId()` và `save()` có khoảng trống
- 2 shipper có thể cùng pass check và cùng tạo assignment

**Trả lời:**

Đúng, có race condition. Giải pháp:

**Cách 1: Pessimistic Locking**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT o FROM Order o WHERE o.id = :orderId")
Optional<Order> findByIdWithLock(@Param("orderId") Long orderId);

@Transactional
public ApiResponse claimOrder(Long orderId, Long shipperId) {
    // Lock order row trước
    Order order = orderRepository.findByIdWithLock(orderId)
        .orElseThrow(...);
    
    // Giờ check và save an toàn
    if (assignmentRepository.existsByOrderId(orderId)) {
        return ApiResponse.error("Đã có shipper nhận");
    }
    // ...
}
```

**Cách 2: Unique Constraint + Exception Handling**
```java
// Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "order_id"))
public class ShipperAssignment { }

// Service
@Transactional
public ApiResponse claimOrder(Long orderId, Long shipperId) {
    try {
        // Bỏ check existsByOrderId, để DB constraint xử lý
        ShipperAssignment assignment = ...;
        assignmentRepository.save(assignment);
        return ApiResponse.success("Đã nhận đơn");
    } catch (DataIntegrityViolationException e) {
        return ApiResponse.error("Đơn hàng đã có shipper khác nhận");
    }
}
```

**Trong code hiện tại đã có unique constraint:**
```java
@OneToOne
@JoinColumn(name = "order_id", nullable = false, unique = true)
private Order order;
```
→ Database sẽ reject nếu 2 assignment cùng order_id, nhưng cần handle exception properly.

---

## Câu 2.2: Khi 100 người cùng mua 1 sản phẩm chỉ còn 5 cái, làm sao đảm bảo không oversell?

**Code hiện tại:**
```java
@Transactional
public ApiResponse createOrderFromCart(Long customerId, CreateOrderRequest request) {
    // Validate stock
    for (CartItem cartItem : cart.getItems()) {
        if (product.getStockQuantity() < cartItem.getQuantity()) {
            return ApiResponse.error("Không đủ số lượng");
        }
    }
    
    // Reserve stock
    Long newReserved = currentReserved + cartItem.getQuantity();
    product.setReservedQuantity(newReserved);
}
```

**Vấn đề:**
- Check `stockQuantity` và update `reservedQuantity` không atomic
- 100 requests có thể cùng pass validation

**Trả lời:**

Cần sử dụng **Optimistic Locking với @Version**:

```java
// Product entity
@Entity
public class Product {
    @Version
    private Long version;  // JPA tự động tăng khi update
    
    private Long stockQuantity;
    private Long reservedQuantity;
}

// Service
@Transactional
public ApiResponse createOrderFromCart(...) {
    try {
        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();
            
            // Available = stock - reserved
            Long available = product.getStockQuantity() - product.getReservedQuantity();
            if (available < cartItem.getQuantity()) {
                return ApiResponse.error("Không đủ số lượng");
            }
            
            // Update reserved (JPA sẽ check version)
            product.setReservedQuantity(
                product.getReservedQuantity() + cartItem.getQuantity()
            );
        }
        // Khi flush, nếu version không khớp → OptimisticLockException
    } catch (OptimisticLockException e) {
        // Retry hoặc báo lỗi
        return ApiResponse.error("Sản phẩm vừa được cập nhật, vui lòng thử lại");
    }
}
```

**Hoặc dùng Pessimistic Lock cho critical section:**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT p FROM Product p WHERE p.id = :id")
Product findByIdForUpdate(@Param("id") Long id);
```

---

# PHẦN 3: CÂU HỎI VỀ THIẾT KẾ HỆ THỐNG

---

## Câu 3.1: Payment expiredAt được set là 1 phút (`plusMinutes(1)`). Tại sao lại ngắn như vậy? Người dùng có kịp thanh toán không?

**Code hiện tại:**
```java
// Payment.java
@PrePersist
protected void onCreate() {
    expiredAt = createdAt.plusMinutes(1); // CHỈ 1 PHÚT!
}
```

**Trả lời:**

Đây là **giá trị test**, trong production nên là 15-30 phút:

```java
@PrePersist
protected void onCreate() {
    expiredAt = createdAt.plusMinutes(15); // 15 phút cho production
}
```

**Lý do chọn 15 phút:**
- Đủ thời gian để user mở app ngân hàng và chuyển khoản
- Không quá lâu để giữ stock reserved
- Cân bằng giữa UX và inventory management

**Cải tiến:**
```java
@Value("${payment.expiration.minutes:15}")
private int paymentExpirationMinutes;

// Inject vào service thay vì hardcode trong entity
```

---

## Câu 3.2: Scheduler chạy mỗi 15 giây để expire payments. Nếu có 1 triệu payments pending, hiệu năng sẽ như thế nào?

**Code hiện tại:**
```java
@Scheduled(fixedRate = 15000) // 15 giây
public void expireOldPayments() {
    List<Payment> expiredPayments = paymentRepository
        .findByStatusAndExpiredAtBefore(PaymentStatus.PENDING, now);
    
    for (Payment payment : expiredPayments) {
        // Xử lý từng payment
        orderService.cancelOrderByCustomer(...);
    }
}
```

**Vấn đề:**
- Load toàn bộ expired payments vào memory
- Xử lý tuần tự, blocking
- Có thể timeout hoặc OOM

**Trả lời:**

Cần tối ưu:

**1. Pagination:**
```java
public void expireOldPayments() {
    int pageSize = 100;
    int page = 0;
    Page<Payment> expiredPage;
    
    do {
        expiredPage = paymentRepository.findByStatusAndExpiredAtBefore(
            PaymentStatus.PENDING, now, PageRequest.of(page, pageSize)
        );
        
        for (Payment payment : expiredPage.getContent()) {
            processExpiredPayment(payment);
        }
        page++;
    } while (expiredPage.hasNext());
}
```

**2. Batch Update (tốt hơn):**
```java
@Modifying
@Query("UPDATE Payment p SET p.status = 'EXPIRED' " +
       "WHERE p.status = 'PENDING' AND p.expiredAt < :now")
int bulkExpirePayments(@Param("now") LocalDateTime now);
```

**3. Async Processing:**
```java
@Async
public CompletableFuture<Void> processExpiredPayment(Payment payment) {
    // Xử lý async
}
```

---

## Câu 3.3: Tại sao dùng `findAll()` rồi filter trong Java thay vì query trực tiếp từ database?

**Code hiện tại:**
```java
// ShipperAssignmentServiceImpl.java
public ApiResponse getAvailableOrdersForShipper() {
    List<Order> orders = orderRepository.findAll().stream()  // LOAD TẤT CẢ!
        .filter(order -> order.getStatus() == OrderStatus.READY_TO_SHIP)
        .filter(order -> order.getGhnOrderCode() == null)
        .filter(this::isHanoiInnerCity)
        .filter(order -> !assignmentRepository.existsByOrderId(order.getId()))
        .collect(Collectors.toList());
}
```

**Vấn đề:**
- Load toàn bộ orders vào memory
- N+1 query với `existsByOrderId()` cho mỗi order
- Không scale được

**Trả lời:**

Cần viết query tối ưu:

```java
// OrderRepository.java
@Query("SELECT o FROM Order o " +
       "WHERE o.status = 'READY_TO_SHIP' " +
       "AND (o.ghnOrderCode IS NULL OR o.ghnOrderCode = '') " +
       "AND o.province LIKE '%Hà Nội%' " +
       "AND o.id NOT IN (SELECT sa.order.id FROM ShipperAssignment sa)")
List<Order> findAvailableOrdersForShipper();

// Service
public ApiResponse getAvailableOrdersForShipper() {
    List<Order> orders = orderRepository.findAvailableOrdersForShipper();
    // Filter isHanoiInnerCity nếu cần logic phức tạp
    return ApiResponse.success("Danh sách đơn", orders);
}
```

---

# PHẦN 4: CÂU HỎI VỀ NGHIỆP VỤ

---

## Câu 4.1: Khi đơn hàng đã xuất kho (READY_TO_SHIP) mà khách hủy, hàng đã xuất xử lý thế nào?

**Code hiện tại:**
```java
@Transactional
public ApiResponse cancelOrderByCustomer(Long orderId, Long customerId, String reason) {
    boolean isExported = (order.getStatus() == OrderStatus.READY_TO_SHIP || 
                         order.getStatus() == OrderStatus.SHIPPING);
    
    for (OrderItem item : order.getItems()) {
        if (isExported) {
            // KHÔNG tự động cộng lại kho
            log.info("Order cancelled after export - needs manual re-import");
        } else {
            // Trừ reserved quantity
            product.setReservedQuantity(newReserved);
        }
    }
}
```

**Trả lời:**

Đây là thiết kế có chủ đích:

**Lý do không tự động cộng lại kho:**
1. Hàng đã xuất có serial number cụ thể
2. Cần kiểm tra tình trạng hàng trước khi nhập lại
3. Có thể hàng bị hư hỏng trong quá trình vận chuyển
4. Cần audit trail cho việc nhập lại

**Quy trình xử lý:**
```
1. Đơn hàng bị hủy sau xuất kho
   ↓
2. Shipper trả hàng về kho
   ↓
3. Nhân viên kho kiểm tra tình trạng
   ↓
4. Tạo phiếu nhập kho hoàn trả (với serial numbers)
   ↓
5. Cập nhật InventoryStock.onHand
```

**Cải tiến có thể làm:**
- Tạo entity `ReturnOrder` để track hàng hoàn trả
- Tự động tạo task cho warehouse staff khi có đơn hủy sau xuất kho

---

## Câu 4.2: Review chỉ check `DELIVERED` và `COMPLETED`. Nếu đơn hàng bị hủy sau khi giao, khách vẫn có thể đánh giá?

**Code hiện tại:**
```java
public ApiResponse createReview(CreateReviewRequest request, Long customerId) {
    if (order.getStatus() != OrderStatus.DELIVERED && 
        order.getStatus() != OrderStatus.COMPLETED) {
        return ApiResponse.error("Chỉ có thể đánh giá sau khi đã nhận hàng");
    }
}
```

**Vấn đề:**
- Không check `CANCELLED`
- Nếu đơn DELIVERED → CANCELLED (hoàn hàng), khách vẫn đánh giá được

**Trả lời:**

Cần bổ sung check:

```java
public ApiResponse createReview(CreateReviewRequest request, Long customerId) {
    // Check không phải đơn đã hủy
    if (order.getStatus() == OrderStatus.CANCELLED) {
        return ApiResponse.error("Không thể đánh giá đơn hàng đã hủy");
    }
    
    // Check đã nhận hàng
    if (order.getStatus() != OrderStatus.DELIVERED && 
        order.getStatus() != OrderStatus.COMPLETED) {
        return ApiResponse.error("Chỉ có thể đánh giá sau khi đã nhận hàng");
    }
    
    // Thêm: Check thời gian (chỉ cho đánh giá trong 30 ngày)
    if (order.getDeliveredAt() != null && 
        order.getDeliveredAt().plusDays(30).isBefore(LocalDateTime.now())) {
        return ApiResponse.error("Đã quá thời hạn đánh giá (30 ngày)");
    }
}
```

---

## Câu 4.3: Shipper nội thành chỉ check tên quận bằng String.contains(). Nếu có quận mới hoặc tên viết khác thì sao?

**Code hiện tại:**
```java
private boolean isHanoiInnerCity(Order order) {
    String[] innerDistricts = {
        "ba đình", "hoàn kiếm", "tây hồ", ...
    };
    
    for (String innerDistrict : innerDistricts) {
        if (district.contains(innerDistrict)) {
            return true;
        }
    }
}
```

**Vấn đề:**
- Hardcode danh sách quận
- Không handle các cách viết khác: "Q. Ba Đình", "Quận Ba-Đình"
- Không thể cập nhật động

**Trả lời:**

Cần cải tiến:

**1. Dùng District Code thay vì tên:**
```java
// Lưu district_code từ GHN API khi checkout
@Column(name = "district_code")
private String districtCode;

// Check bằng code
private static final Set<String> HANOI_INNER_DISTRICT_CODES = Set.of(
    "1490", "1491", "1492", ... // Codes từ GHN
);

private boolean isHanoiInnerCity(Order order) {
    return HANOI_INNER_DISTRICT_CODES.contains(order.getDistrictCode());
}
```

**2. Hoặc lưu vào database:**
```java
@Entity
public class ShippingZone {
    private String provinceCode;
    private String districtCode;
    private boolean internalShipping; // true = shipper nội bộ
}

// Query
boolean isInternal = shippingZoneRepository
    .existsByDistrictCodeAndInternalShippingTrue(order.getDistrictCode());
```

---

# PHẦN 5: CÂU HỎI VỀ HIỆU NĂNG VÀ SCALE

---

## Câu 5.1: JWT secret key đang hardcode trong application.properties. Đây có phải best practice?

**Code hiện tại:**
```properties
app.jwt.secret=ThisIsASecretKeyForJWTGeneration_ChangeMe123456789
```

**Trả lời:**

KHÔNG, đây là **security risk**:
- Secret key bị commit vào Git
- Ai có access repo đều biết secret
- Có thể forge JWT token

**Best practice:**

```properties
# application.properties
app.jwt.secret=${JWT_SECRET}

# Hoặc dùng Spring Cloud Config / AWS Secrets Manager
```

```bash
# Environment variable
export JWT_SECRET=your-super-secret-key-here

# Hoặc trong docker-compose.yml
environment:
  - JWT_SECRET=${JWT_SECRET}
```

---

## Câu 5.2: Không thấy index nào được định nghĩa trong entities. Query sẽ chậm khi data lớn?

**Trả lời:**

Đúng, cần thêm indexes:

```java
@Entity
@Table(name = "orders", indexes = {
    @Index(name = "idx_order_customer", columnList = "customer_id"),
    @Index(name = "idx_order_status", columnList = "status"),
    @Index(name = "idx_order_created", columnList = "created_at"),
    @Index(name = "idx_order_code", columnList = "order_code")
})
public class Order { }

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payment_status_expired", columnList = "status, expired_at"),
    @Index(name = "idx_payment_order", columnList = "order_id")
})
public class Payment { }

@Entity
@Table(name = "product_details", indexes = {
    @Index(name = "idx_serial_number", columnList = "serial_number", unique = true),
    @Index(name = "idx_product_status", columnList = "warehouse_product_id, status")
})
public class ProductDetail { }
```

---

## Câu 5.3: CORS đang allow tất cả origins (`*`). Trong production có nên như vậy?

**Code hiện tại:**
```java
configuration.setAllowedOriginPatterns(Arrays.asList("*"));
```

**Trả lời:**

KHÔNG, cần restrict:

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    
    // Production: chỉ allow domains cụ thể
    if ("prod".equals(activeProfile)) {
        configuration.setAllowedOrigins(Arrays.asList(
            "https://yourdomain.com",
            "https://www.yourdomain.com",
            "https://admin.yourdomain.com"
        ));
    } else {
        // Dev: allow localhost
        configuration.setAllowedOriginPatterns(Arrays.asList(
            "http://localhost:*",
            "https://*.ngrok-free.app"
        ));
    }
}
```

---

# PHẦN 6: CÂU HỎI TỔNG HỢP

---

## Câu 6.1: Nếu SePay server down, hệ thống xử lý thế nào?

**Trả lời:**

Hiện tại: Không có fallback, user không thể thanh toán online.

**Cải tiến:**
1. **Circuit Breaker pattern:**
```java
@CircuitBreaker(name = "sepay", fallbackMethod = "sepayFallback")
public String generateQrCode(...) {
    // Call SePay API
}

public String sepayFallback(Exception e) {
    // Fallback: Hiển thị thông tin chuyển khoản thủ công
    return "Vui lòng chuyển khoản thủ công...";
}
```

2. **Retry mechanism:**
```java
@Retryable(value = {SepayException.class}, maxAttempts = 3)
public void callSepayApi() { }
```

3. **Health check endpoint** để monitor SePay status

---

## Câu 6.2: Làm sao đảm bảo dữ liệu nhất quán giữa Product.stockQuantity và InventoryStock.onHand?

**Code hiện tại:**
```java
// Sau khi update InventoryStock
private void syncStockWithProduct(WarehouseProduct wp, Long newOnHand) {
    Product product = productRepository.findByWarehouseProduct(wp);
    if (product != null) {
        product.setStockQuantity(newOnHand);
        productRepository.save(product);
    }
}
```

**Vấn đề:**
- 2 bảng lưu cùng 1 thông tin (redundancy)
- Có thể bị out of sync nếu sync fail

**Trả lời:**

**Cách 1: Bỏ redundancy, query từ InventoryStock:**
```java
// Product entity
@Transient
public Long getStockQuantity() {
    if (warehouseProduct != null && warehouseProduct.getInventoryStock() != null) {
        return warehouseProduct.getInventoryStock().getOnHand();
    }
    return 0L;
}
```

**Cách 2: Event-driven sync:**
```java
@TransactionalEventListener
public void onInventoryChanged(InventoryChangedEvent event) {
    syncStockWithProduct(event.getWarehouseProduct(), event.getNewOnHand());
}
```

**Cách 3: Database trigger** (nếu cần đảm bảo 100%)

---

## Câu 6.3: Tóm tắt các điểm cần cải thiện trước khi deploy production?

**CRITICAL (Phải sửa):**
1. ❌ Xóa/bảo vệ endpoint `/test-webhook`
2. ❌ Implement signature verification cho webhook
3. ❌ Fix IDOR trong ShipperAssignmentController
4. ❌ Move secrets ra environment variables
5. ❌ Restrict CORS origins

**HIGH (Nên sửa):**
1. ⚠️ Thêm Pessimistic/Optimistic locking cho concurrent operations
2. ⚠️ Tối ưu queries (tránh findAll + filter)
3. ⚠️ Thêm database indexes
4. ⚠️ Tăng payment expiration time (15 phút)
5. ⚠️ Pagination cho scheduler

**MEDIUM (Cải tiến):**
1. 📝 Dùng district code thay vì tên
2. 📝 Thêm caching layer (Redis)
3. 📝 Circuit breaker cho external APIs
4. 📝 Monitoring và alerting
5. 📝 Backup strategy

---

# KẾT LUẬN

Đồ án có kiến trúc tốt, phân module rõ ràng, nhưng còn một số lỗ hổng bảo mật và vấn đề về concurrency cần được xử lý trước khi đưa vào production. Các vấn đề này phổ biến trong các dự án học thuật và có thể được giải quyết với các giải pháp đã đề xuất ở trên.
