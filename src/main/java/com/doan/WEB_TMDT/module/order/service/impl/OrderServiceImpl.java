package com.doan.WEB_TMDT.module.order.service.impl;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.auth.entity.Customer;
import com.doan.WEB_TMDT.module.auth.repository.CustomerRepository;
import com.doan.WEB_TMDT.module.cart.entity.Cart;
import com.doan.WEB_TMDT.module.cart.entity.CartItem;
import com.doan.WEB_TMDT.module.cart.repository.CartRepository;
import com.doan.WEB_TMDT.module.order.dto.CreateOrderRequest;
import com.doan.WEB_TMDT.module.order.dto.OrderItemResponse;
import com.doan.WEB_TMDT.module.order.dto.OrderResponse;
import com.doan.WEB_TMDT.module.order.entity.Order;
import com.doan.WEB_TMDT.module.order.entity.OrderItem;
import com.doan.WEB_TMDT.module.order.entity.OrderStatus;
import com.doan.WEB_TMDT.module.order.entity.PaymentStatus;
import com.doan.WEB_TMDT.module.order.repository.OrderRepository;
import com.doan.WEB_TMDT.module.order.service.OrderService;
import com.doan.WEB_TMDT.module.payment.repository.PaymentRepository;
import com.doan.WEB_TMDT.module.product.entity.Product;
import com.doan.WEB_TMDT.module.accounting.listener.OrderStatusChangedEvent;
import com.doan.WEB_TMDT.module.product.repository.ProductImageRepository;
import com.doan.WEB_TMDT.module.product.repository.ProductRepository;
import com.doan.WEB_TMDT.module.shipping.dto.CalculateShippingFeeRequest;
import com.doan.WEB_TMDT.module.shipping.dto.CreateGHNOrderRequest;
import com.doan.WEB_TMDT.module.shipping.dto.GHNOrderDetailResponse;
import com.doan.WEB_TMDT.module.shipping.service.ShippingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final ShippingService shippingService;
    private final ProductImageRepository productImageRepository;
    private final ProductRepository productRepository;
    private final com.doan.WEB_TMDT.module.inventory.service.InventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.doan.WEB_TMDT.module.order.repository.ShipperAssignmentRepository shipperAssignmentRepository;

    @Override
    public Long getCustomerIdByEmail(String email) {
        log.info("OrderService - Getting customerId for email: {}", email);
        Customer customer = customerRepository.findByUserEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khách hàng với email: " + email));
        log.info("OrderService - Found customerId: {}", customer.getId());
        return customer.getId();
    }

    /**
     * Helper method to publish order status change event for accounting module
     */
    private void publishOrderStatusChangeEvent(Order order, OrderStatus oldStatus, OrderStatus newStatus) {
        try {
            OrderStatusChangedEvent event = new OrderStatusChangedEvent(this, order, oldStatus, newStatus);
            eventPublisher.publishEvent(event);
            log.info("Published OrderStatusChangedEvent for order: {} ({} -> {})", 
                order.getOrderCode(), oldStatus, newStatus);
        } catch (Exception e) {
            log.error("Failed to publish OrderStatusChangedEvent for order: {}", order.getOrderCode(), e);
            // Don't fail the order process if event publishing fails
        }
    }

    @Override
    @Transactional
    public ApiResponse createOrderFromCart(Long customerId, CreateOrderRequest request) {
        // 1. Get customer
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy khách hàng"));

        // 2. Get cart
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> new RuntimeException("Giỏ hàng trống"));

        if (cart.getItems().isEmpty()) {
            return ApiResponse.error("Giỏ hàng trống");
        }

        // 2.1 Lọc các item đã chọn (nếu có selectedItemIds)
        List<CartItem> selectedCartItems;
        if (request.getSelectedItemIds() != null && !request.getSelectedItemIds().isEmpty()) {
            selectedCartItems = cart.getItems().stream()
                    .filter(item -> request.getSelectedItemIds().contains(item.getId()))
                    .collect(java.util.stream.Collectors.toList());
            
            if (selectedCartItems.isEmpty()) {
                return ApiResponse.error("Không tìm thấy sản phẩm đã chọn trong giỏ hàng");
            }
        } else {
            // Nếu không có selectedItemIds thì lấy tất cả
            selectedCartItems = new ArrayList<>(cart.getItems());
        }

        // 3. Validate stock for selected items WITH LOCK để tránh race condition
        // Khi 100 người đặt cùng lúc, chỉ 1 người được xử lý tại 1 thời điểm
        for (CartItem cartItem : selectedCartItems) {
            // Lấy product với lock - các request khác phải đợi
            Product product = productRepository.findByIdWithLock(cartItem.getProduct().getId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy sản phẩm"));
            
            // Tính số lượng có thể bán = tồn kho - đã giữ
            Long availableQty = (product.getStockQuantity() != null ? product.getStockQuantity() : 0L)
                    - (product.getReservedQuantity() != null ? product.getReservedQuantity() : 0L);
            
            if (availableQty < cartItem.getQuantity()) {
                if (availableQty <= 0) {
                    return ApiResponse.error("Rất tiếc, sản phẩm " + product.getName() + " đã hết hàng");
                } else {
                    return ApiResponse.error("Sản phẩm " + product.getName() + " chỉ còn " + availableQty + " sản phẩm, vui lòng giảm số lượng");
                }
            }
            
            // Cập nhật lại cartItem với product đã lock
            cartItem.setProduct(product);
        }

        // 4. Calculate totals (chỉ tính các item đã chọn)
        Double subtotal = selectedCartItems.stream()
                .mapToDouble(item -> item.getPrice() * item.getQuantity())
                .sum();
        Double shippingFee = request.getShippingFee();
        Double discount = 0.0; // TODO: Apply voucher
        Double total = subtotal + shippingFee - discount;

        // 5. Create order
        String orderCode = generateOrderCode();
        // Build full address with proper order: address, ward name, district, province
        String wardDisplay = request.getWardName() != null && !request.getWardName().isEmpty() 
                ? request.getWardName() 
                : request.getWard(); // Fallback to ward code if name not available
        String fullAddress = String.format("%s, %s, %s, %s",
                request.getAddress(), wardDisplay, 
                request.getDistrict(), request.getProvince());

        // Xác định status dựa trên payment method
        OrderStatus initialStatus;
        LocalDateTime confirmedTime = null;
        
        if ("SEPAY".equals(request.getPaymentMethod())) {
            // Thanh toán online → PENDING_PAYMENT (chờ thanh toán)
            initialStatus = OrderStatus.PENDING_PAYMENT;
        } else {
            // COD → CONFIRMED (tự động xác nhận, chờ chuẩn bị hàng)
            initialStatus = OrderStatus.CONFIRMED;
            confirmedTime = LocalDateTime.now();
        }
        
        Order order = Order.builder()
                .orderCode(orderCode)
                .customer(customer)
                .shippingAddress(fullAddress)
                .province(request.getProvince())
                .district(request.getDistrict())
                .ward(request.getWard())
                .wardName(request.getWardName())
                .address(request.getAddress())
                .note(request.getNote())
                .subtotal(subtotal)
                .shippingFee(shippingFee)
                .discount(discount)
                .total(total)
                .status(initialStatus)
                .paymentStatus(PaymentStatus.UNPAID)
                .paymentMethod(request.getPaymentMethod()) // Lưu phương thức thanh toán
                .confirmedAt(confirmedTime)
                .build();

        // 6. Create order items and reserve stock (giữ hàng)
        // Note: stockQuantity không thay đổi ở đây
        // Chỉ khi xuất kho (warehouse export) thì mới trừ stockQuantity
        List<OrderItem> orderItems = new ArrayList<>();
        for (CartItem cartItem : selectedCartItems) {
            Product product = cartItem.getProduct();
            
            // Reserve stock (giữ hàng để không bán cho người khác)
            Long currentReserved = product.getReservedQuantity() != null ? product.getReservedQuantity() : 0L;
            Long newReserved = currentReserved + cartItem.getQuantity();
            product.setReservedQuantity(newReserved);
            
            // Đồng bộ reserved với InventoryStock
            if (product.getWarehouseProduct() != null) {
                inventoryService.syncReservedQuantity(product.getWarehouseProduct().getId(), newReserved);
            }
            
            log.info("Product {} reserved: {} -> {} (ordered: {})", 
                product.getName(), currentReserved, newReserved, cartItem.getQuantity());
            
            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .productName(product.getName())
                    .price(cartItem.getPrice())
                    .quantity(cartItem.getQuantity())
                    .subtotal(cartItem.getPrice() * cartItem.getQuantity())
                    .reserved(true)  // Đã giữ hàng
                    .exported(false) // Chưa xuất kho
                    .build();
            
            orderItems.add(orderItem);
        }
        order.setItems(orderItems);

        // 7. Save order
        Order savedOrder = orderRepository.save(order);


        
        // Note: GHN order creation moved to warehouse export process
        // When warehouse staff:
        // 1. Creates export slip
        // 2. Assigns serial numbers
        // 3. Confirms export → Then call GHN API

        // 9. Chỉ xóa các item đã mua, giữ lại các item khác trong giỏ hàng
        for (CartItem cartItem : selectedCartItems) {
            cart.getItems().remove(cartItem);
        }
        cartRepository.save(cart);

        log.info("Created order {} for customer {} with {} items (removed from cart)", 
                orderCode, customerId, selectedCartItems.size());

        // 10. Return response
        OrderResponse response = toOrderResponse(savedOrder);
        return ApiResponse.success("Đặt hàng thành công", response);
    }

    @Override
    public ApiResponse getOrderById(Long orderId, Long customerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Verify ownership
        if (!order.getCustomer().getId().equals(customerId)) {
            return ApiResponse.error("Bạn không có quyền xem đơn hàng này");
        }

        OrderResponse response = toOrderResponse(order);
        return ApiResponse.success("Chi tiết đơn hàng", response);
    }

    @Override
    public ApiResponse getOrderByCode(String orderCode, Long customerId) {
        Order order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Verify ownership
        if (!order.getCustomer().getId().equals(customerId)) {
            return ApiResponse.error("Bạn không có quyền xem đơn hàng này");
        }

        OrderResponse response = toOrderResponse(order);
        return ApiResponse.success("Chi tiết đơn hàng", response);
    }

    @Override
    public ApiResponse getMyOrders(Long customerId) {

        List<Order> orders = orderRepository.findByCustomerId(customerId);

        // Log order details
        orders.forEach(order -> {
        });
        
        List<OrderResponse> responses = orders.stream()
                .map(this::toOrderResponse)
                .collect(Collectors.toList());
        return ApiResponse.success("Danh sách đơn hàng", responses);
    }

    @Override
    @Transactional
    public ApiResponse  cancelOrderByCustomer(Long orderId, Long customerId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Verify ownership
        if (!order.getCustomer().getId().equals(customerId)) {
            return ApiResponse.error("Bạn không có quyền hủy đơn hàng này");
        }

        // Chỉ cho phép hủy khi chưa giao hàng
        if (order.getStatus() == OrderStatus.DELIVERED) {
            return ApiResponse.error("Không thể hủy đơn hàng đã giao thành công");
        }
        
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return ApiResponse.error("Đơn hàng đã bị hủy trước đó");
        }

        // Nếu đang chờ thanh toán (PENDING_PAYMENT) → XÓA KHỎI DB
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            log.info("Deleting order {} (PENDING_PAYMENT) by customer {}", order.getOrderCode(), customerId);
            
            // Giải phóng stock đã reserve
            for (OrderItem item : order.getItems()) {
                Product product = item.getProduct();
                Long currentReserved = product.getReservedQuantity() != null ? product.getReservedQuantity() : 0L;
                Long newReserved = Math.max(0, currentReserved - item.getQuantity());
                product.setReservedQuantity(newReserved);
                
                // Đồng bộ reserved với InventoryStock
                if (product.getWarehouseProduct() != null) {
                    inventoryService.syncReservedQuantity(product.getWarehouseProduct().getId(), newReserved);
                }
            }
            
            // Xóa payment trước (nếu có) để tránh foreign key constraint
            if (order.getPaymentId() != null) {
                try {
                    paymentRepository.findByOrderId(order.getId()).ifPresent(payment -> {
                        paymentRepository.delete(payment);
                        log.info("Deleted payment for order {}", order.getOrderCode());
                    });
                } catch (Exception e) {
                    log.warn("Could not delete payment for order {}: {}", order.getOrderCode(), e.getMessage());
                }
            }
            
            // Xóa đơn hàng
            orderRepository.delete(order);
            
            return ApiResponse.success("Đã hủy đơn hàng");
        }

        // Nếu đã CONFIRMED trở đi → Chuyển sang CANCELLED (lưu lại)
        // Check payment status - nếu đã thanh toán thì cần hoàn tiền
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            // TODO: Tích hợp API hoàn tiền
            log.warn("Order {} đã thanh toán, cần xử lý hoàn tiền", order.getOrderCode());
        }

        // Restore stock for cancelled order
        // Logic khác nhau tùy theo trạng thái:
        // - CONFIRMED: Hàng đang được giữ (reserved) → Trả lại reserved quantity
        // - READY_TO_SHIP, SHIPPING: Hàng đã xuất kho → KHÔNG tự động cộng lại kho (cần nhập kho thủ công sau)
        boolean isExported = (order.getStatus() == OrderStatus.READY_TO_SHIP || 
                             order.getStatus() == OrderStatus.SHIPPING);
        
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            
            if (isExported) {
                // Đơn đã xuất kho → KHÔNG tự động cộng lại kho
                // Hàng cần được nhập lại kho thủ công sau này
                log.info("Order {} cancelled after export - product {} needs manual re-import to warehouse", 
                    order.getOrderCode(), product.getName());
            } else {
                // Đơn đang giữ hàng (CONFIRMED) → Chỉ trừ reserved quantity
                Long currentReserved = product.getReservedQuantity() != null ? product.getReservedQuantity() : 0L;
                Long newReserved = Math.max(0, currentReserved - item.getQuantity());
                product.setReservedQuantity(newReserved);
                
                // Đồng bộ reserved với InventoryStock
                if (product.getWarehouseProduct() != null) {
                    inventoryService.syncReservedQuantity(product.getWarehouseProduct().getId(), newReserved);
                }
                
                log.info("Released reserved quantity for product {} (CONFIRMED cancelled): {} -> {} (released: {})", 
                    product.getName(), currentReserved, newReserved, item.getQuantity());
            }
        }

        // Cancel order (chuyển status sang CANCELLED)
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelReason(reason != null ? reason : "Khách hàng hủy đơn");
        orderRepository.save(order);

        log.info("Cancelled order {} by customer {}", order.getOrderCode(), customerId);

        OrderResponse response = toOrderResponse(order);
        return ApiResponse.success("Đã hủy đơn hàng" + 
            (order.getPaymentStatus() == PaymentStatus.PAID ? ". Tiền sẽ được hoàn lại trong 3-5 ngày làm việc" : ""), 
            response);
    }

    @Override
    public ApiResponse getShippingStatus(Long orderId, Long customerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Verify ownership
        if (!order.getCustomer().getId().equals(customerId)) {
            return ApiResponse.error("Bạn không có quyền xem đơn hàng này");
        }

        // Check if order has GHN tracking
        if (order.getGhnOrderCode() == null || order.getGhnOrderCode().isEmpty()) {
            return ApiResponse.error("Đơn hàng này không có mã vận đơn GHN");
        }

        try {
            // Get GHN order detail
            GHNOrderDetailResponse ghnDetail =
                shippingService.getGHNOrderDetail(order.getGhnOrderCode());
            
            // Update order status from GHN
            if (ghnDetail.getStatus() != null) {
                order.setGhnShippingStatus(ghnDetail.getStatus());
                orderRepository.save(order);
            }
            
            return ApiResponse.success("Trạng thái vận chuyển", ghnDetail);
            
        } catch (Exception e) {
            log.error("Error getting shipping status for order {}: {}", orderId, e.getMessage());
            return ApiResponse.error("Không thể lấy thông tin vận chuyển: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public ApiResponse confirmReceived(Long orderId, Long customerId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));
        
        // Kiểm tra đơn hàng thuộc về customer này
        if (!order.getCustomer().getId().equals(customerId)) {
            return ApiResponse.error("Bạn không có quyền thao tác với đơn hàng này");
        }
        
        // Chỉ cho phép xác nhận khi đơn ở trạng thái DELIVERED
        if (order.getStatus() != OrderStatus.DELIVERED) {
            return ApiResponse.error("Chỉ có thể xác nhận nhận hàng khi đơn ở trạng thái 'Đã giao'");
        }
        
        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        orderRepository.save(order);
        
        // Publish event for accounting
        publishOrderStatusChangeEvent(order, oldStatus, OrderStatus.COMPLETED);
        
        log.info("Customer {} confirmed received order {}", customerId, order.getOrderCode());
        
        return ApiResponse.success("Đã xác nhận nhận hàng thành công", toOrderResponse(order));
    }

    // Helper methods

    private String generateOrderCode() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int random = new Random().nextInt(9999);
        String code = "ORD" + date + String.format("%04d", random);

        // Check if exists
        if (orderRepository.existsByOrderCode(code)) {
            return generateOrderCode(); // Retry
        }

        return code;
    }

    private OrderResponse toOrderResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(this::toOrderItemResponse)
                .collect(Collectors.toList());

        Customer customer = order.getCustomer();
        
        // Lấy thông tin shipper nếu có
        Long shipperId = null;
        String shipperName = null;
        String shipperPhone = null;
        
        try {
            var assignmentOpt = shipperAssignmentRepository.findByOrderId(order.getId());
            if (assignmentOpt.isPresent()) {
                var assignment = assignmentOpt.get();
                var shipper = assignment.getShipper();
                if (shipper != null) {
                    shipperId = shipper.getId();
                    shipperName = shipper.getFullName();
                    shipperPhone = shipper.getPhone();
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch shipper info for order {}: {}", order.getId(), e.getMessage());
        }
        
        return OrderResponse.builder()
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .status(order.getStatus().name())
                .paymentStatus(order.getPaymentStatus().name())
                .paymentMethod(order.getPaymentMethod())
                .customerId(customer.getId())
                .customerName(customer.getFullName())
                .customerPhone(customer.getPhone())
                .customerEmail(customer.getUser().getEmail())
                .shippingAddress(order.getShippingAddress())
                .province(order.getProvince())
                .district(order.getDistrict())
                .ward(order.getWard())
                .wardName(order.getWardName())
                .address(order.getAddress())
                .note(order.getNote())
                .items(items)
                .subtotal(order.getSubtotal())
                .shippingFee(order.getShippingFee())
                .discount(order.getDiscount())
                .total(order.getTotal())
                .createdAt(order.getCreatedAt())
                .confirmedAt(order.getConfirmedAt())
                .shippedAt(order.getShippedAt())
                .deliveredAt(order.getDeliveredAt())
                .completedAt(order.getCompletedAt())
                .cancelledAt(order.getCancelledAt())
                .cancelReason(order.getCancelReason())
                .ghnOrderCode(order.getGhnOrderCode())
                .ghnShippingStatus(order.getGhnShippingStatus())
                .ghnCreatedAt(order.getGhnCreatedAt())
                .ghnExpectedDeliveryTime(order.getGhnExpectedDeliveryTime())
                .shipperId(shipperId)
                .shipperName(shipperName)
                .shipperPhone(shipperPhone)
                .build();
    }

    private OrderItemResponse toOrderItemResponse(OrderItem item) {
        // Lấy ảnh đầu tiên từ product_images
        String productImage = productImageRepository.findByProductIdOrderByDisplayOrderAsc(item.getProduct().getId())
                .stream()
                .findFirst()
                .map(img -> img.getImageUrl())
                .orElse(null);

        return OrderItemResponse.builder()
                .itemId(item.getId())
                .productId(item.getProduct().getId())
                .productName(item.getProductName())
                .productImage(productImage)
                .productSku(item.getProduct().getSku())
                .price(item.getPrice())
                .quantity(item.getQuantity())
                .subtotal(item.getSubtotal())
                .serialNumber(item.getSerialNumber())
                .reserved(item.getReserved())
                .exported(item.getExported())
                .build();
    }

    // Admin/Staff methods

    @Override
    public ApiResponse getAllOrders(String status, int page, int size) {
        List<Order> orders;
        
        if (status != null && !status.isEmpty() && !status.equalsIgnoreCase("ALL")) {
            try {
                OrderStatus orderStatus = OrderStatus.valueOf(status.toUpperCase());
                orders = orderRepository.findByStatus(orderStatus);
            } catch (IllegalArgumentException e) {
                return ApiResponse.error("Trạng thái không hợp lệ");
            }
        } else {
            orders = orderRepository.findAll();
        }
        
        // Sort by created date desc
        orders.sort((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));
        
        List<OrderResponse> responses = orders.stream()
                .map(this::toOrderResponse)
                .collect(Collectors.toList());
        
        return ApiResponse.success("Danh sách đơn hàng", responses);
    }

    // @Override
    // @Transactional
    // public ApiResponse confirmOrder(Long orderId) {
    //     Order order = orderRepository.findById(orderId)
    //             .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

    //     if (order.getStatus() != OrderStatus.PENDING) {
    //         return ApiResponse.error("Chỉ có thể xác nhận đơn hàng ở trạng thái chờ xác nhận");
    //     }

    //     order.setStatus(OrderStatus.CONFIRMED);
    //     order.setConfirmedAt(LocalDateTime.now());
    //     orderRepository.save(order);

    //     log.info("Confirmed order {}", order.getOrderCode());

    //     OrderResponse response = toOrderResponse(order);
    //     return ApiResponse.success("Đã xác nhận đơn hàng", response);
    // }

    @Override
    @Transactional
    public ApiResponse updateOrderStatus(Long orderId, String status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        try {
            OrderStatus oldStatus = order.getStatus();
            OrderStatus newStatus = OrderStatus.valueOf(status.toUpperCase());
            order.setStatus(newStatus);
            
            // Update timestamps based on status
            switch (newStatus) {
                case CONFIRMED:
                    if (order.getConfirmedAt() == null) {
                        order.setConfirmedAt(LocalDateTime.now());
                    }
                    break;
                case SHIPPING:
                    if (order.getShippedAt() == null) {
                        order.setShippedAt(LocalDateTime.now());
                    }
                    break;
                case DELIVERED:
                    if (order.getDeliveredAt() == null) {
                        order.setDeliveredAt(LocalDateTime.now());
                    }
                    order.setPaymentStatus(PaymentStatus.PAID);
                    break;
                case CANCELLED:
                    if (order.getCancelledAt() == null) {
                        order.setCancelledAt(LocalDateTime.now());
                    }
                    break;
            }
            
            orderRepository.save(order);
            
            // Publish event for accounting automation
            publishOrderStatusChangeEvent(order, oldStatus, newStatus);
            
            log.info("Updated order {} status to {}", order.getOrderCode(), newStatus);

            OrderResponse response = toOrderResponse(order);
            return ApiResponse.success("Đã cập nhật trạng thái đơn hàng", response);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error("Trạng thái không hợp lệ");
        }
    }

    @Override
    @Transactional
    public ApiResponse markAsShipping(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        if (order.getStatus() != OrderStatus.CONFIRMED) {
            return ApiResponse.error("Chỉ có thể chuyển sang đang giao hàng từ trạng thái đã xác nhận");
        }

        order.setStatus(OrderStatus.SHIPPING);
        order.setShippedAt(LocalDateTime.now());
        orderRepository.save(order);

        log.info("Marked order {} as shipping", order.getOrderCode());

        OrderResponse response = toOrderResponse(order);
        return ApiResponse.success("Đã chuyển đơn hàng sang đang giao", response);
    }

    @Override
    @Transactional
    public ApiResponse markShippingFromReady(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Chỉ cho phép chuyển từ READY_TO_SHIP sang SHIPPING
        if (order.getStatus() != OrderStatus.READY_TO_SHIP) {
            return ApiResponse.error("Chỉ có thể chuyển sang đang giao hàng từ trạng thái 'Đã chuẩn bị hàng - Đợi tài xế'");
        }

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.SHIPPING);
        order.setShippedAt(LocalDateTime.now());
        orderRepository.save(order);

        // Publish event for accounting module
        publishOrderStatusChangeEvent(order, oldStatus, OrderStatus.SHIPPING);

        log.info("Sales staff manually marked order {} as SHIPPING (from READY_TO_SHIP)", order.getOrderCode());

        OrderResponse response = toOrderResponse(order);
        return ApiResponse.success("Đã chuyển đơn hàng sang đang giao hàng", response);
    }

    @Override
    @Transactional
    public ApiResponse markAsDelivered(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Cho phép chuyển từ READY_TO_SHIP (shipper nội thành đã nhận) hoặc SHIPPING
        if (order.getStatus() != OrderStatus.SHIPPING && order.getStatus() != OrderStatus.READY_TO_SHIP) {
            return ApiResponse.error("Chỉ có thể chuyển sang đã giao từ trạng thái đang giao hàng hoặc đã chuẩn bị hàng");
        }

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        if (order.getShippedAt() == null) {
            order.setShippedAt(LocalDateTime.now()); // Set shipped time if not set
        }
        order.setPaymentStatus(PaymentStatus.PAID); // Mark as paid when delivered (COD)
        orderRepository.save(order);

        // Publish event for accounting automation
        publishOrderStatusChangeEvent(order, oldStatus, OrderStatus.DELIVERED);

        log.info("Marked order {} as delivered", order.getOrderCode());

        OrderResponse response = toOrderResponse(order);
        return ApiResponse.success("Đã xác nhận giao hàng thành công", response);
    }

    @Override
    public ApiResponse getOrderById(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        OrderResponse response = toOrderResponse(order);
        return ApiResponse.success("Chi tiết đơn hàng", response);
    }

    @Override
    public ApiResponse getOrderStatistics() {
        List<Order> allOrders = orderRepository.findAll();
        
        long totalOrders = allOrders.size();
        long pendingOrders = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.PENDING_PAYMENT).count();
        long confirmedOrders = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.CONFIRMED).count();
        long shippingOrders = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.SHIPPING).count();
        long deliveredOrders = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();
        long cancelledOrders = allOrders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();
        
        Double totalRevenue = allOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                .mapToDouble(Order::getTotal)
                .sum();
        
        var statistics = new java.util.HashMap<String, Object>();
        statistics.put("totalOrders", totalOrders);
        statistics.put("pendingOrders", pendingOrders);
        statistics.put("confirmedOrders", confirmedOrders);
        statistics.put("shippingOrders", shippingOrders);
        statistics.put("deliveredOrders", deliveredOrders);
        statistics.put("cancelledOrders", cancelledOrders);
        statistics.put("totalRevenue", totalRevenue);
        
        return ApiResponse.success("Thống kê đơn hàng", statistics);
    }

    @Override
    @Transactional
    public ApiResponse cancelOrder(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Admin/Staff can cancel any order except delivered
        if (order.getStatus() == OrderStatus.DELIVERED) {
            return ApiResponse.error("Không thể hủy đơn hàng đã giao thành công");
        }

        // Restore stock for cancelled order
        // Logic khác nhau tùy theo trạng thái:
        // - PENDING_PAYMENT, CONFIRMED: Hàng đang được giữ (reserved) → Trả lại reserved quantity
        // - READY_TO_SHIP, SHIPPING: Hàng đã xuất kho → KHÔNG tự động cộng lại kho (cần nhập kho thủ công sau)
        boolean isExported = (order.getStatus() == OrderStatus.READY_TO_SHIP || 
                             order.getStatus() == OrderStatus.SHIPPING);
        
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            
            if (isExported) {
                // Đơn đã xuất kho → KHÔNG tự động cộng lại kho
                // Hàng cần được nhập lại kho thủ công sau này
                log.info("Order {} cancelled after export - product {} needs manual re-import to warehouse", 
                    order.getOrderCode(), product.getName());
            } else {
                // Đơn đang giữ hàng (PENDING_PAYMENT, CONFIRMED) → Chỉ trừ reserved quantity
                Long currentReserved = product.getReservedQuantity() != null ? product.getReservedQuantity() : 0L;
                Long newReserved = Math.max(0, currentReserved - item.getQuantity());
                product.setReservedQuantity(newReserved);
                
                // Đồng bộ reserved với InventoryStockz
                if (product.getWarehouseProduct() != null) {
                    inventoryService.syncReservedQuantity(product.getWarehouseProduct().getId(), newReserved);
                }
                
                log.info("Released reserved quantity for product {} (reserved order cancelled): {} -> {} (released: {})", 
                    product.getName(), currentReserved, newReserved, item.getQuantity());
            }
        }
        
        // Cancel order
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelReason(reason != null ? reason : "Hủy bởi nhân viên");
        orderRepository.save(order);

        log.info("Cancelled order {} by admin/staff", order.getOrderCode());

        OrderResponse response = toOrderResponse(order);
        return ApiResponse.success("Đã hủy đơn hàng", response);
    }

    @Override
    public ApiResponse getShippingStatusAdmin(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Check if order has GHN tracking
        if (order.getGhnOrderCode() == null || order.getGhnOrderCode().isEmpty()) {
            return ApiResponse.error("Đơn hàng này không có mã vận đơn GHN");
        }

        try {
            // Get GHN order detail
            GHNOrderDetailResponse ghnDetail =
                shippingService.getGHNOrderDetail(order.getGhnOrderCode());
            
            // Update order status from GHN
            if (ghnDetail.getStatus() != null) {
                order.setGhnShippingStatus(ghnDetail.getStatus());
                orderRepository.save(order);
            }
            
            return ApiResponse.success("Trạng thái vận chuyển", ghnDetail);
            
        } catch (Exception e) {
            log.error("Error getting shipping status for order {}: {}", orderId, e.getMessage());
            return ApiResponse.error("Không thể lấy thông tin vận chuyển: " + e.getMessage());
        }
    }
    
    // Helper methods for GHN integration
    
    private Integer getDistrictIdForGHN(String province, String district) {
        // Reuse the logic from ShippingService
        // For now, return a default or call a helper
        // This is a simplified version - in production, you'd want to cache this
        try {
            CalculateShippingFeeRequest feeRequest =
                CalculateShippingFeeRequest.builder()
                    .province(province)
                    .district(district)
                    .weight(1000.0)
                    .value(0.0)
                    .build();
            
            // This will internally get the district ID
            shippingService.calculateShippingFee(feeRequest);
            
            // For now, return default Hà Đông district
            return 1485;
        } catch (Exception e) {
            log.warn("Could not get district ID, using default: {}", e.getMessage());
            return 1485;
        }
    }
    
    private List<CreateGHNOrderRequest.GHNOrderItem> buildGHNItems(Order order) {
        List<CreateGHNOrderRequest.GHNOrderItem> items = new ArrayList<>();
        
        for (OrderItem item : order.getItems()) {
            items.add(CreateGHNOrderRequest.GHNOrderItem.builder()
                    .name(item.getProductName())
                    .code(item.getProduct().getSku())
                    .quantity(item.getQuantity())
                    .price(item.getPrice().intValue())
                    .build());
        }
        
        return items;
    }
    
    @Override
    public ApiResponse getOrdersPendingExport() {
        // Lấy các đơn CONFIRMED mà chưa có trong export_orders
        List<Order> orders = orderRepository.findByStatusAndNotExported(OrderStatus.CONFIRMED);
        
        List<OrderResponse> responses = orders.stream()
                .map(this::toOrderResponse)
                .toList();
        
        log.info("Found {} orders pending export (CONFIRMED and not exported yet)", orders.size());
        return ApiResponse.success("Danh sách đơn hàng chờ xuất kho", responses);
    }

    @Override
    public ApiResponse getOrdersByCustomerId(Long customerId) {
        List<Order> orders = orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId);
        
        List<OrderResponse> responses = orders.stream()
                .map(this::toOrderResponse)
                .toList();
        
        return ApiResponse.success("Danh sách đơn hàng của khách hàng", responses);
    }
}
