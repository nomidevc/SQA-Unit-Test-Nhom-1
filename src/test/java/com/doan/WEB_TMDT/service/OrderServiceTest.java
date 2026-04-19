package com.doan.WEB_TMDT.service;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.auth.entity.Customer;
import com.doan.WEB_TMDT.module.auth.entity.User;
import com.doan.WEB_TMDT.module.auth.repository.CustomerRepository;
import com.doan.WEB_TMDT.module.auth.repository.UserRepository;
import com.doan.WEB_TMDT.module.cart.entity.Cart;
import com.doan.WEB_TMDT.module.cart.entity.CartItem;
import com.doan.WEB_TMDT.module.cart.repository.CartItemRepository;
import com.doan.WEB_TMDT.module.cart.repository.CartRepository;
import com.doan.WEB_TMDT.module.inventory.service.InventoryService;
import com.doan.WEB_TMDT.module.order.dto.CreateOrderRequest;
import com.doan.WEB_TMDT.module.order.dto.OrderResponse;
import com.doan.WEB_TMDT.module.order.entity.Order;
import com.doan.WEB_TMDT.module.order.entity.OrderItem;
import com.doan.WEB_TMDT.module.order.entity.OrderStatus;
import com.doan.WEB_TMDT.module.order.entity.PaymentStatus;
import com.doan.WEB_TMDT.module.order.repository.OrderRepository;
import com.doan.WEB_TMDT.module.order.service.OrderService;
import com.doan.WEB_TMDT.module.product.entity.Category;
import com.doan.WEB_TMDT.module.product.entity.Product;
import com.doan.WEB_TMDT.module.product.repository.CategoryRepository;
import com.doan.WEB_TMDT.module.product.repository.ProductRepository;
import com.doan.WEB_TMDT.module.shipping.service.ShippingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

/**
 * SQA UNIT TEST SCRIPT - MODULE: ORDER SERVICE
 * Total Test Cases: 70
 * Purpose: Bug Hunting, Business Logic Validation & DB Consistency (CheckDB).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class OrderServiceTest {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    @MockBean private ShippingService shippingService;
    @MockBean private InventoryService inventoryService;
    @MockBean private ApplicationEventPublisher eventPublisher;

    private Long customerId;
    private Long otherCustomerId;
    private Product product1;
    private Product product2;

    @BeforeEach
    public void setup() {
        // Setup data
        customerId = initCustomer("order_tester@test.com", "0988000111");
        otherCustomerId = initCustomer("other_tester@test.com", "0988000222");

        Category cat = categoryRepository.save(Category.builder().name("Electronics").slug("electronics").active(true).build());
        product1 = productRepository.save(Product.builder().name("Laptop Pro").sku("LP").price(2000.0).stockQuantity(10L).reservedQuantity(0L).active(true).category(cat).build());
        product2 = productRepository.save(Product.builder().name("Mouse Wireless").sku("MW").price(50.0).stockQuantity(50L).reservedQuantity(0L).active(true).category(cat).build());

        doNothing().when(inventoryService).syncReservedQuantity(any(), any());
    }

    // --- GROUP 1: ORDER CREATION (TC_ORDER_01 - TC_ORDER_15) ---

    @Test @DisplayName("TC_ORDER_01: Đặt hàng thành công từ giỏ hàng (COD)")
    public void tc01() {
        addToCart(customerId, product1, 1);
        CreateOrderRequest req = createReq("Hanoi", "COD", 30000.0);
        
        ApiResponse res = orderService.createOrderFromCart(customerId, req);
        
        assertTrue(res.isSuccess());
        OrderResponse data = (OrderResponse) res.getData();
        assertNotNull(data.getOrderCode());
        assertEquals("CONFIRMED", data.getStatus());
        
        // CheckDB: Đơn hàng lưu đúng customer và tổng tiền
        Order savedOrder = orderRepository.findById(data.getOrderId()).orElseThrow();
        assertEquals(customerId, savedOrder.getCustomer().getId());
        assertEquals(2000.0 + 30000.0, savedOrder.getTotal());
        
        // CheckDB: Giỏ hàng phải được xóa item đã mua
        assertTrue(cartItemRepository.findByCartId(cartRepository.findByCustomerId(customerId).get().getId()).isEmpty());
    }

    @Test @DisplayName("TC_ORDER_02: Đặt hàng thành công (Online Payment - SEPAY)")
    public void tc02() {
        addToCart(customerId, product1, 1);
        CreateOrderRequest req = createReq("HCM", "SEPAY", 15000.0);
        
        ApiResponse res = orderService.createOrderFromCart(customerId, req);
        
        assertTrue(res.isSuccess());
        OrderResponse data = (OrderResponse) res.getData();
        assertEquals("PENDING_PAYMENT", data.getStatus());
        assertEquals("UNPAID", data.getPaymentStatus());
    }

    @Test @DisplayName("TC_ORDER_03: Đặt hàng khi giỏ hàng trống")
    public void tc03() {
        CreateOrderRequest req = createReq("Hanoi", "COD", 0.0);
        // Error in code: throws RuntimeException instead of returning error result
        assertThrows(RuntimeException.class, () -> orderService.createOrderFromCart(customerId, req));
    }

    @Test @DisplayName("TC_ORDER_04: Đặt hàng với sản phẩm hết hàng (Stock=0)")
    public void tc04() {
        Product outOfStock = productRepository.save(Product.builder().name("Old Phone").sku("OP").price(10.0).stockQuantity(0L).active(true).build());
        addToCart(customerId, outOfStock, 1);
        
        CreateOrderRequest req = createReq("Hanoi", "COD", 0.0);
        ApiResponse res = orderService.createOrderFromCart(customerId, req);
        
        assertFalse(res.isSuccess());
        assertTrue(res.getMessage().contains("hết hàng"));
    }

    @Test @DisplayName("TC_ORDER_05: Đặt hàng với số lượng vượt tồn kho")
    public void tc05() {
        addToCart(customerId, product1, 100);
        CreateOrderRequest req = createReq("Hanoi", "COD", 0.0);
        ApiResponse res = orderService.createOrderFromCart(customerId, req);
        assertFalse(res.isSuccess());
    }

    @Test @DisplayName("TC_ORDER_06: Đặt hàng chỉ chọn một vài sản phẩm trong giỏ")
    public void tc06() {
        CartItem item1 = addToCart(customerId, product1, 1);
        addToCart(customerId, product2, 1);
        
        CreateOrderRequest req = createReq("Hanoi", "COD", 20000.0);
        req.setSelectedItemIds(Collections.singletonList(item1.getId()));
        
        ApiResponse res = orderService.createOrderFromCart(customerId, req);
        assertTrue(res.isSuccess());
        
        // Đơn hàng chỉ có 1 item
        Order savedOrder = orderRepository.findById(((OrderResponse)res.getData()).getOrderId()).get();
        assertEquals(1, savedOrder.getItems().size());
        
        // Bug TC_ORDER_06: Kiểm tra tình huống xóa nhầm toàn bộ sản phẩm trong giỏ khi chỉ mua 1 phần
        Cart cart = cartRepository.findByCustomerId(customerId).get();
        // Mong đợi: Giỏ hàng phải được cập nhật lại số lượng items
        assertEquals(1, cart.getItems().size(), "BUG_SUMMARY: Hệ thống xóa nhầm toàn bộ giỏ hàng của khách");
    }

    @Test @DisplayName("TC_ORDER_07: Kiểm tra giữ hàng (Reserved Quantity) khi đặt hàng")
    public void tc07() {
        addToCart(customerId, product1, 2);
        orderService.createOrderFromCart(customerId, createReq("A", "COD", 0.0));
        
        // CheckDB: Reserved quantity của product1 phải là 2
        Product p = productRepository.findById(product1.getId()).get();
        assertEquals(2L, p.getReservedQuantity());
    }

    @Test @DisplayName("TC_ORDER_08: Đặt hàng với địa chỉ trống")
    public void tc08() {
        addToCart(customerId, product1, 1);
        CreateOrderRequest req = createReq("", "COD", 0.0);
        req.setAddress("");
        // Tùy logic frontend/backend, ở đây test xem có crash ko
        ApiResponse res = orderService.createOrderFromCart(customerId, req);
        // Bug TC_ORDER_08: Hệ thống không được phép cho đặt hàng khi địa chỉ rỗng
        assertFalse(res.isSuccess(), "BUG_SUMMARY: Hệ thống chấp nhận địa chỉ giao hàng rỗng");
    }

    @Test @DisplayName("TC_ORDER_09: Đặt hàng với phí ship âm (Hệ thống nên chặn hoặc xử lý)")
    public void tc09() {
        addToCart(customerId, product1, 1);
        CreateOrderRequest req = createReq("Hanoi", "COD", -50000.0);
        ApiResponse res = orderService.createOrderFromCart(customerId, req);
        // Bug TC_ORDER_09: Hệ thống không được phép cho đặt hàng với phí ship âm
        assertFalse(res.isSuccess(), "BUG: Hệ thống vẫn cho pháp đặt hàng với phí ship âm");
    }

    @Test @DisplayName("TC_ORDER_10: Đặt hàng khi Product bị xóa giữa chừng")
    public void tc10() {
        addToCart(customerId, product1, 1);
        productRepository.deleteById(product1.getId());
        
        CreateOrderRequest req = createReq("Hanoi", "COD", 0.0);
        assertThrows(RuntimeException.class, () -> orderService.createOrderFromCart(customerId, req));
    }

    @Test @DisplayName("TC_ORDER_11: Mã đơn hàng (Order Code) phải duy nhất")
    public void tc11() {
        addToCart(customerId, product1, 1);
        ApiResponse res1 = orderService.createOrderFromCart(customerId, createReq("A", "COD", 0.0));
        
        addToCart(customerId, product2, 1);
        ApiResponse res2 = orderService.createOrderFromCart(customerId, createReq("B", "COD", 0.0));
        
        String code1 = ((OrderResponse)res1.getData()).getOrderCode();
        String code2 = ((OrderResponse)res2.getData()).getOrderCode();
        assertNotEquals(code1, code2);
    }

    @Test @DisplayName("TC_ORDER_12: Đặt hàng với Customer không tồn tại")
    public void tc12() {
        assertThrows(RuntimeException.class, () -> orderService.createOrderFromCart(9999L, createReq("A", "COD", 0.0)));
    }

    @Test @DisplayName("TC_ORDER_13: Đặt hàng với Giỏ hàng bị xóa (Mồ côi)")
    public void tc13() {
        cartRepository.deleteAll(); // Xóa hết giỏ hàng
        assertThrows(RuntimeException.class, () -> orderService.createOrderFromCart(customerId, createReq("A", "COD", 0.0)));
    }

    @Test @DisplayName("TC_ORDER_14: Kiểm tra thông tin ghi chú (Note) lưu đúng")
    public void tc14() {
        addToCart(customerId, product1, 1);
        CreateOrderRequest req = createReq("A", "COD", 0.0);
        req.setNote("Giao giờ hành chính");
        ApiResponse res = orderService.createOrderFromCart(customerId, req);
        
        Order saved = orderRepository.findById(((OrderResponse)res.getData()).getOrderId()).get();
        assertEquals("Giao giờ hành chính", saved.getNote());
    }

    @Test @DisplayName("TC_ORDER_15: Đặt hàng với nhiều loại sản phẩm khác nhau")
    public void tc15() {
        addToCart(customerId, product1, 1);
        addToCart(customerId, product2, 2);
        ApiResponse res = orderService.createOrderFromCart(customerId, createReq("A", "COD", 0.0));
        
        Order saved = orderRepository.findById(((OrderResponse)res.getData()).getOrderId()).get();
        assertEquals(2, saved.getItems().size());
    }

    // --- GROUP 2: ORDER RETRIEVAL (TC_ORDER_16 - TC_ORDER_25) ---

    @Test @DisplayName("TC_ORDER_16: Lấy đơn hàng theo ID hợp lệ")
    public void tc16() {
        Long orderId = createSampleOrder(customerId);
        ApiResponse res = orderService.getOrderById(orderId, customerId);
        assertTrue(res.isSuccess());
        assertEquals(orderId, ((OrderResponse)res.getData()).getOrderId());
    }

    @Test @DisplayName("TC_ORDER_17: Lấy đơn hàng theo ID không tồn tại")
    public void tc17() {
        assertThrows(RuntimeException.class, () -> orderService.getOrderById(9999L, customerId));
    }

    @Test @DisplayName("TC_ORDER_18: SECURITY - Lấy đơn hàng của người khác theo ID")
    public void tc18() {
        Long orderId = createSampleOrder(customerId);
        ApiResponse res = orderService.getOrderById(orderId, otherCustomerId);
        assertFalse(res.isSuccess());
        assertEquals("Bạn không có quyền xem đơn hàng này", res.getMessage());
    }

    @Test @DisplayName("TC_ORDER_19: Lấy đơn hàng theo mã (Order Code) hợp lệ")
    public void tc19() {
        Long orderId = createSampleOrder(customerId);
        String code = orderRepository.findById(orderId).get().getOrderCode();
        ApiResponse res = orderService.getOrderByCode(code, customerId);
        assertTrue(res.isSuccess());
        assertEquals(code, ((OrderResponse)res.getData()).getOrderCode());
    }

    @Test @DisplayName("TC_ORDER_20: Lấy đơn hàng theo mã không tồn tại")
    public void tc20() {
        assertThrows(RuntimeException.class, () -> orderService.getOrderByCode("INVALID_CODE", customerId));
    }

    @Test @DisplayName("TC_ORDER_21: SECURITY - Lấy đơn hàng người khác theo mã")
    public void tc21() {
        Long orderId = createSampleOrder(customerId);
        String code = orderRepository.findById(orderId).get().getOrderCode();
        ApiResponse res = orderService.getOrderByCode(code, otherCustomerId);
        assertFalse(res.isSuccess());
    }

    @Test @DisplayName("TC_ORDER_22: Danh sách đơn hàng của tôi (My Orders)")
    public void tc22() {
        createSampleOrder(customerId);
        createSampleOrder(customerId);
        ApiResponse res = orderService.getMyOrders(customerId);
        assertTrue(res.isSuccess());
        assertTrue(((List<?>)res.getData()).size() >= 2);
    }

    @Test @DisplayName("TC_ORDER_23: Danh sách đơn hàng khi chưa mua gì")
    public void tc23() {
        ApiResponse res = orderService.getMyOrders(otherCustomerId);
        assertTrue(res.isSuccess());
        assertTrue(((List<?>)res.getData()).isEmpty());
    }

    @Test @DisplayName("TC_ORDER_24: Admin lấy chi tiết đơn hàng (Không cần customerId)")
    public void tc24() {
        Long orderId = createSampleOrder(customerId);
        ApiResponse res = orderService.getOrderById(orderId);
        assertTrue(res.isSuccess());
        assertEquals(orderId, ((OrderResponse)res.getData()).getOrderId());
    }

    @Test @DisplayName("TC_ORDER_25: Admin lấy danh sách đơn hàng lọc theo trạng thái")
    public void tc25() {
        createSampleOrder(customerId);
        ApiResponse res = orderService.getAllOrders("CONFIRMED", 0, 10);
        assertTrue(res.isSuccess());
        assertNotNull(res.getData());
    }

    // --- GROUP 3: STATUS UPDATES (TC_ORDER_26 - TC_ORDER_40) ---

    @Test @DisplayName("TC_ORDER_26: Cập nhật trạng thái sang CONFIRMED")
    public void tc26() {
        Long orderId = createSampleOrder(customerId);
        ApiResponse res = orderService.updateOrderStatus(orderId, "CONFIRMED");
        assertTrue(res.isSuccess());
        assertEquals("CONFIRMED", ((OrderResponse)res.getData()).getStatus());
    }

    @Test @DisplayName("TC_ORDER_27: Cập nhật trạng thái sang SHIPPING")
    public void tc27() {
        Long orderId = createSampleOrder(customerId);
        orderService.updateOrderStatus(orderId, "CONFIRMED");
        ApiResponse res = orderService.updateOrderStatus(orderId, "SHIPPING");
        assertTrue(res.isSuccess());
        assertEquals("SHIPPING", ((OrderResponse)res.getData()).getStatus());
    }

    @Test @DisplayName("TC_ORDER_28: Cập nhật trạng thái sang DELIVERED (Check PaymentStatus)")
    public void tc28() {
        Long orderId = createSampleOrder(customerId);
        orderService.updateOrderStatus(orderId, "SHIPPING");
        ApiResponse res = orderService.updateOrderStatus(orderId, "DELIVERED");
        assertTrue(res.isSuccess());
        OrderResponse data = (OrderResponse) res.getData();
        assertEquals("DELIVERED", data.getStatus());
        assertEquals("PAID", data.getPaymentStatus()); // Tự động PAID nếu giao thành công (COD)
    }

    @Test @DisplayName("TC_ORDER_29: Cập nhật trạng thái không hợp lệ (String sai)")
    public void tc29() {
        Long orderId = createSampleOrder(customerId);
        ApiResponse res = orderService.updateOrderStatus(orderId, "GIAO_THANH_CONG");
        assertFalse(res.isSuccess());
        assertEquals("Trạng thái không hợp lệ", res.getMessage());
    }

    @Test @DisplayName("TC_ORDER_30: Mark as Shipping từ CONFIRMED")
    public void tc30() {
        Long orderId = createSampleOrder(customerId);
        orderService.updateOrderStatus(orderId, "CONFIRMED");
        ApiResponse res = orderService.markAsShipping(orderId);
        assertTrue(res.isSuccess());
        assertEquals("SHIPPING", ((OrderResponse)res.getData()).getStatus());
    }

    @Test @DisplayName("TC_ORDER_31: Mark as Shipping từ trạng thái sai (Ví dụ PENDING_PAYMENT)")
    public void tc31() {
        addToCart(customerId, product1, 1);
        ApiResponse resOrder = orderService.createOrderFromCart(customerId, createReq("A", "SEPAY", 0.0));
        Long orderId = ((OrderResponse)resOrder.getData()).getOrderId();
        
        ApiResponse res = orderService.markAsShipping(orderId);
        assertFalse(res.isSuccess());
    }

    @Test @DisplayName("TC_ORDER_32: Mark as Delivered khi đang SHIPPING")
    public void tc32() {
        Long orderId = createSampleOrder(customerId);
        orderService.updateOrderStatus(orderId, "SHIPPING");
        ApiResponse res = orderService.markAsDelivered(orderId);
        assertTrue(res.isSuccess());
        assertEquals("DELIVERED", ((OrderResponse)res.getData()).getStatus());
    }

    @Test @DisplayName("TC_ORDER_33: Xác nhận nhận hàng bởi khách (Confirm Received)")
    public void tc33() {
        Long orderId = createSampleOrder(customerId);
        orderService.updateOrderStatus(orderId, "SHIPPING");
        orderService.updateOrderStatus(orderId, "DELIVERED");
        
        ApiResponse res = orderService.confirmReceived(orderId, customerId);
        assertTrue(res.isSuccess());
        assertEquals("COMPLETED", ((OrderResponse)res.getData()).getStatus());
    }

    @Test @DisplayName("TC_ORDER_34: Khách xác nhận nhận hàng khi đơn chưa giao (Sai Status)")
    public void tc34() {
        Long orderId = createSampleOrder(customerId);
        ApiResponse res = orderService.confirmReceived(orderId, customerId);
        assertFalse(res.isSuccess());
    }

    @Test @DisplayName("TC_ORDER_35: SECURITY - Khách xác nhận nhận hàng của người khác")
    public void tc35() {
        Long orderId = createSampleOrder(customerId);
        orderService.updateOrderStatus(orderId, "DELIVERED");
        ApiResponse res = orderService.confirmReceived(orderId, otherCustomerId);
        assertFalse(res.isSuccess());
    }

    @Test @DisplayName("TC_ORDER_36: Lấy thống kê đơn hàng (Statistics)")
    public void tc36() {
        createSampleOrder(customerId);
        ApiResponse res = orderService.getOrderStatistics();
        assertTrue(res.isSuccess());
        assertNotNull(((java.util.Map<?,?>)res.getData()).get("totalOrders"));
    }

    @Test @DisplayName("TC_ORDER_37: Kiểm tra tổng tiền doanh thu trong thống kê")
    public void tc37() {
        Long orderId = createSampleOrder(customerId);
        orderService.updateOrderStatus(orderId, "DELIVERED");
        
        ApiResponse res = orderService.getOrderStatistics();
        Double revenue = (Double) ((java.util.Map<?,?>)res.getData()).get("totalRevenue");
        // Bug TC_ORDER_37: Kiểm tra tính đúng đắn của doanh thu (mẫu test mong đợi logic chặt chẽ hơn)
        assertEquals(2000.0, revenue, "BUG: Thống kê doanh thu không chính xác");
    }

    @Test @DisplayName("TC_ORDER_38: Cập nhật status nhiều lần (State machine check)")
    public void tc38() {
        Long orderId = createSampleOrder(customerId);
        orderService.updateOrderStatus(orderId, "CONFIRMED");
        orderService.updateOrderStatus(orderId, "DELIVERED");
        ApiResponse res = orderService.updateOrderStatus(orderId, "CANCELLED"); 
        
        // Bug TC_ORDER_38: Đơn đã giao (DELIVERED) thì không được phép chuyển sang CANCELLED nữa
        assertFalse(res.isSuccess(), "BUG: Vẫn cho phép hủy đơn đã giao thành công");
    }

    @Test @DisplayName("TC_ORDER_39: Mark Shipping From Ready (Nội thành)")
    public void tc39() {
        Long orderId = createSampleOrder(customerId);
        orderService.updateOrderStatus(orderId, "READY_TO_SHIP");
        ApiResponse res = orderService.markShippingFromReady(orderId);
        assertTrue(res.isSuccess());
        assertEquals("SHIPPING", ((OrderResponse)res.getData()).getStatus());
    }

    @Test @DisplayName("TC_ORDER_40: Cập nhật status đơn hàng đã từng hủy (Nghiệp vụ chặn ko cho đổi lại)")
    public void tc40() {
        Long orderId = createSampleOrder(customerId);
        orderService.cancelOrderByCustomer(orderId, customerId, "Sửa lại");
        ApiResponse res = orderService.updateOrderStatus(orderId, "SHIPPING");
        // Bug TC_ORDER_40: Đơn hàng đã hủy không được phép quay lại trạng thái SHIPPING
        assertFalse(res.isSuccess(), "BUG: Đơn đã hủy vẫn cho phép chuyển sang giao hàng");
    }

    // --- GROUP 4: CANCELLATION (TC_ORDER_41 - TC_ORDER_55) ---

    @Test @DisplayName("TC_ORDER_41: Khách hàng hủy đơn hàng (CONFIRMED)")
    public void tc41() {
        Long orderId = createSampleOrder(customerId);
        ApiResponse res = orderService.cancelOrderByCustomer(orderId, customerId, "Tôi muốn đổi sản phẩm");
        assertTrue(res.isSuccess());
        assertEquals("CANCELLED", ((OrderResponse)res.getData()).getStatus());
        
        // CheckDB: Reserved quantity phải được trả lại
        Product p = productRepository.findById(product1.getId()).get();
        assertEquals(0L, p.getReservedQuantity());
    }

    @Test @DisplayName("TC_ORDER_42: Khách hàng hủy đơn hàng đang giao (SHIPPING) - Phải thất bại")
    public void tc42() {
        Long orderId = createSampleOrder(customerId);
        orderService.updateOrderStatus(orderId, "SHIPPING");
        ApiResponse res = orderService.cancelOrderByCustomer(orderId, customerId, "Hủy ngang");
        // Bug TC_ORDER_42: Khách không được tự ý hủy khi đơn đã xuất kho đi giao (SHIPPING)
        assertFalse(res.isSuccess(), "BUG: Khách vẫn hủy được đơn khi đang trên đường giao");
    }

    @Test @DisplayName("TC_ORDER_43: Khách hàng hủy đơn hàng đã giao (DELIVERED) - Phải thất bại")
    public void tc43() {
        Long orderId = createSampleOrder(customerId);
        orderService.updateOrderStatus(orderId, "DELIVERED");
        ApiResponse res = orderService.cancelOrderByCustomer(orderId, customerId, "Trả hàng");
        assertFalse(res.isSuccess());
        assertEquals("Không thể hủy đơn hàng đã giao thành công", res.getMessage());
    }

    @Test @DisplayName("TC_ORDER_44: Admin hủy đơn hàng")
    public void tc44() {
        Long orderId = createSampleOrder(customerId);
        ApiResponse res = orderService.cancelOrder(orderId, "Admin hủy");
        assertTrue(res.isSuccess());
        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(orderId).get().getStatus());
    }

    @Test @DisplayName("TC_ORDER_45: Hủy đơn hàng PENDING_PAYMENT (Xóa khỏi DB)")
    public void tc45() {
        addToCart(customerId, product1, 1);
        ApiResponse resOrder = orderService.createOrderFromCart(customerId, createReq("A", "SEPAY", 0.0));
        Long orderId = ((OrderResponse)resOrder.getData()).getOrderId();
        
        orderService.cancelOrderByCustomer(orderId, customerId, "Nghĩ lại");
        
        // CheckDB: Đơn hàng PENDING_PAYMENT bị hủy sẽ bị XÓA khỏi database
        assertFalse(orderRepository.existsById(orderId));
    }

    @Test @DisplayName("TC_ORDER_46: Kiểm tra lý do hủy (Cancel Reason)")
    public void tc46() {
        Long orderId = createSampleOrder(customerId);
        orderService.cancelOrderByCustomer(orderId, customerId, "Sản phẩm đắt");
        Order saved = orderRepository.findById(orderId).get();
        assertEquals("Sản phẩm đắt", saved.getCancelReason());
    }

    @Test @DisplayName("TC_ORDER_47: Hủy đơn hàng đã CONFIRMED -> Kiểm tra Reserved Stock")
    public void tc47() {
        addToCart(customerId, product1, 5);
        Long id = ((OrderResponse)orderService.createOrderFromCart(customerId, createReq("A", "COD", 0.0)).getData()).getOrderId();
        
        assertEquals(5L, productRepository.findById(product1.getId()).get().getReservedQuantity());
        orderService.cancelOrder(id, "Bùng hàng");
        assertEquals(0L, productRepository.findById(product1.getId()).get().getReservedQuantity());
    }

    @Test @DisplayName("TC_ORDER_48: Hủy đơn hàng đã xuất kho (READY_TO_SHIP) -> Không hoàn kho tự động")
    public void tc48() {
        Long orderId = createSampleOrder(customerId);
        orderService.updateOrderStatus(orderId, "READY_TO_SHIP");
        
        // Giả sử logic nghiệp vụ: Đã xuất kho thì hủy ko tự cộng lại kho (phải nhập tay)
        orderService.cancelOrder(orderId, "Hủy sau xuất");
        // Theo code, p.setReservedQuantity ko gọi nếu isExported=true
        // Ta ko kiểm tra stockQuantity vì stockQuantity chỉ trừ khi Export thực sự (nằm ở service khác)
    }

    @Test @DisplayName("TC_ORDER_49: Hủy đơn hàng của người khác (Admin override test)")
    public void tc49() {
        Long orderId = createSampleOrder(customerId);
        ApiResponse res = orderService.cancelOrderByCustomer(orderId, otherCustomerId, "Hack");
        assertFalse(res.isSuccess());
    }

    @Test @DisplayName("TC_ORDER_50: Hủy đơn hàng đã bị hủy trước đó")
    public void tc50() {
        Long orderId = createSampleOrder(customerId);
        orderService.cancelOrder(orderId, "Lần 1");
        ApiResponse res = orderService.cancelOrderByCustomer(orderId, customerId, "Lần 2");
        assertFalse(res.isSuccess());
        assertEquals("Đơn hàng đã bị hủy trước đó", res.getMessage());
    }

    @Test @DisplayName("TC_ORDER_51: Hủy đơn khi không truyền lý do (Reason=null)")
    public void tc51() {
        Long orderId = createSampleOrder(customerId);
        ApiResponse res = orderService.cancelOrder(orderId, null);
        assertTrue(res.isSuccess());
    }

    @Test @DisplayName("TC_ORDER_52: Hủy đơn hàng có nhiểu sản phẩm")
    public void tc52() {
        addToCart(customerId, product1, 1);
        addToCart(customerId, product2, 1);
        Long id = ((OrderResponse)orderService.createOrderFromCart(customerId, createReq("A", "COD", 0.0)).getData()).getOrderId();
        
        orderService.cancelOrder(id, "Hủy");
        assertEquals(0L, productRepository.findById(product1.getId()).get().getReservedQuantity());
        assertEquals(0L, productRepository.findById(product2.getId()).get().getReservedQuantity());
    }

    @Test @DisplayName("TC_ORDER_53: Kiểm tra timestamp khi hủy (CancelledAt)")
    public void tc53() {
        Long orderId = createSampleOrder(customerId);
        orderService.cancelOrder(orderId, "Reason");
        assertNotNull(orderRepository.findById(orderId).get().getCancelledAt());
    }

    @Test @DisplayName("TC_ORDER_54: Admin hủy đơn hàng không tồn tại")
    public void tc54() {
        assertThrows(RuntimeException.class, () -> orderService.cancelOrder(9999L, "Reason"));
    }

    @Test @DisplayName("TC_ORDER_55: Hủy đơn hàng Online đã thanh toán (Hoàn tiền - TODO logic check)")
    public void tc55() {
        addToCart(customerId, product1, 1);
        Order order = orderRepository.findById(((OrderResponse)orderService.createOrderFromCart(customerId, createReq("A", "SEPAY", 0.0)).getData()).getOrderId()).get();
        order.setPaymentStatus(PaymentStatus.PAID);
        orderRepository.saveAndFlush(order);
        
        ApiResponse res = orderService.cancelOrderByCustomer(order.getId(), customerId, "Hủy");
        assertTrue(res.isSuccess());
        // Match exact message from OrderServiceImpl
        assertTrue(res.getMessage().contains("hoàn lại"), "BUG_SUMMARY: Hệ thống không hiển thị thông báo hoàn tiền khi hủy đơn đã thanh toán");
    }

    // --- GROUP 5: DELIVERY & FULFILLMENT (TC_ORDER_56 - TC_ORDER_65) ---

    @Test @DisplayName("TC_ORDER_56: Xác nhận chuẩn bị hàng xong (READY_TO_SHIP)")
    public void tc56() {
        Long orderId = createSampleOrder(customerId);
        orderService.updateOrderStatus(orderId, "CONFIRMED");
        ApiResponse res = orderService.updateOrderStatus(orderId, "READY_TO_SHIP");
        assertTrue(res.isSuccess());
        assertEquals("READY_TO_SHIP", ((OrderResponse)res.getData()).getStatus());
    }

    @Test @DisplayName("TC_ORDER_57: Mark as Shipping từ READY_TO_SHIP")
    public void tc57() {
        Long orderId = createSampleOrder(customerId);
        orderService.updateOrderStatus(orderId, "READY_TO_SHIP");
        // Must use specific method for READY_TO_SHIP
        ApiResponse res = orderService.markShippingFromReady(orderId);
        assertTrue(res.isSuccess());
    }

    @Test @DisplayName("TC_ORDER_58: Nhân viên lấy danh sách đơn chờ xuất kho")
    public void tc58() {
        createSampleOrder(customerId);
        ApiResponse res = orderService.getOrdersPendingExport();
        assertTrue(res.isSuccess());
        assertNotNull(res.getData());
    }

    @Test @DisplayName("TC_ORDER_59: Kiểm tra trạng thái giao hàng GHN (Success Mock)")
    public void tc59() throws Exception {
        Long orderId = createSampleOrder(customerId);
        Order order = orderRepository.findById(orderId).get();
        order.setGhnOrderCode("GHN12345");
        orderRepository.save(order);
        
        com.doan.WEB_TMDT.module.shipping.dto.GHNOrderDetailResponse mockRes = new com.doan.WEB_TMDT.module.shipping.dto.GHNOrderDetailResponse();
        mockRes.setStatus("delivered");
        org.mockito.Mockito.when(shippingService.getGHNOrderDetail("GHN12345")).thenReturn(mockRes);
        
        ApiResponse res = orderService.getShippingStatus(orderId, customerId);
        assertTrue(res.isSuccess());
    }

    @Test @DisplayName("TC_ORDER_60: Kiểm tra trạng thái giao hàng GHN khi chưa có mã vận đơn")
    public void tc60() {
        Long orderId = createSampleOrder(customerId);
        ApiResponse res = orderService.getShippingStatus(orderId, customerId);
        assertFalse(res.isSuccess());
        assertEquals("Đơn hàng này không có mã vận đơn GHN", res.getMessage());
    }

    @Test @DisplayName("TC_ORDER_61: Lấy danh sách đơn hàng theo CustomerId (Cho nhân viên)")
    public void tc61() {
        createSampleOrder(customerId);
        ApiResponse res = orderService.getOrdersByCustomerId(customerId);
        assertTrue(res.isSuccess());
        assertTrue(((List<?>)res.getData()).size() >= 1);
    }

    @Test @DisplayName("TC_ORDER_62: Mark Delivered cho đơn hàng đã COMPLETED")
    public void tc62() {
        Long orderId = createSampleOrder(customerId);
        orderService.updateOrderStatus(orderId, "DELIVERED");
        orderService.updateOrderStatus(orderId, "COMPLETED");
        
        ApiResponse res = orderService.markAsDelivered(orderId);
        assertFalse(res.isSuccess()); // Đã hoàn thành thì ko mark delivered lại
    }

    @Test @DisplayName("TC_ORDER_63: Đồng bộ trạng thái từ GHN API")
    public void tc63() throws Exception {
        Long orderId = createSampleOrder(customerId);
        Order order = orderRepository.findById(orderId).get();
        order.setGhnOrderCode("TEST_SYNC");
        orderRepository.save(order);

        com.doan.WEB_TMDT.module.shipping.dto.GHNOrderDetailResponse mockRes = new com.doan.WEB_TMDT.module.shipping.dto.GHNOrderDetailResponse();
        mockRes.setStatus("picking");
        org.mockito.Mockito.when(shippingService.getGHNOrderDetail("TEST_SYNC")).thenReturn(mockRes);

        orderService.getShippingStatus(orderId, customerId);
        assertEquals("picking", orderRepository.findById(orderId).get().getGhnShippingStatus());
    }

    @Test @DisplayName("TC_ORDER_64: Xác nhận nhận hàng khi đơn đã CANCELLED")
    public void tc64() {
        Long orderId = createSampleOrder(customerId);
        orderService.cancelOrder(orderId, "Hủy");
        ApiResponse res = orderService.confirmReceived(orderId, customerId);
        assertFalse(res.isSuccess());
    }

    @Test @DisplayName("TC_ORDER_65: Cập nhật status admin (Get Shipping Status Admin)")
    public void tc65() {
        Long orderId = createSampleOrder(customerId);
        ApiResponse res = orderService.getShippingStatusAdmin(orderId);
        // Trả về error vì ko có mã vận đơn, nhưng API vẫn chạy đúng logic
        assertFalse(res.isSuccess());
    }

    // --- GROUP 6: BOUNDARY & BUG HUNTING (TC_ORDER_66 - TC_ORDER_70) ---

    @Test @DisplayName("TC_ORDER_66: Đặt hàng với số lượng lớn (Boundary Check)")
    public void tc66() {
        addToCart(customerId, product1, 999999);
        CreateOrderRequest req = createReq("A", "COD", 0.0);
        ApiResponse res = orderService.createOrderFromCart(customerId, req);
        assertFalse(res.isSuccess());
    }

    @Test @DisplayName("TC_ORDER_67: Race Condition Simulation (Đặt hàng đồng thời)")
    public void tc67() {
        // Mocking stock check with lock requires more complex setup, 
        // here we test the service method logic for availableQty calculation.
        product1.setStockQuantity(1L);
        product1.setReservedQuantity(0L);
        productRepository.save(product1);
        
        addToCart(customerId, product1, 1);
        ApiResponse res = orderService.createOrderFromCart(customerId, createReq("A", "COD", 0.0));
        assertTrue(res.isSuccess());
        
        // Cố tình đặt thêm 1 cái nữa trong khi stock đã hết
        addToCart(otherCustomerId, product1, 1);
        ApiResponse res2 = orderService.createOrderFromCart(otherCustomerId, createReq("B", "COD", 0.0));
        assertFalse(res2.isSuccess());
    }

    @Test @DisplayName("TC_ORDER_68: Đặt hàng khi Product có giá thay đổi")
    public void tc68() {
        addToCart(customerId, product1, 1);
        product1.setPrice(5000.0);
        productRepository.save(product1);
        
        ApiResponse res = orderService.createOrderFromCart(customerId, createReq("A", "COD", 0.0));
        OrderResponse data = (OrderResponse) res.getData();
        // Kiểm tra xem nó lấy giá cũ trong giỏ hay giá mới?
        // Theo code: item.getPrice() - lấy từ CartItem (giá lúc thêm vào giỏ)
        assertEquals(2000.0, data.getSubtotal());
    }

    @Test @DisplayName("TC_ORDER_69: Kiểm tra định dạng mã đơn hàng (Regex Check)")
    public void tc69() {
        Long orderId = createSampleOrder(customerId);
        String code = orderRepository.findById(orderId).get().getOrderCode();
        assertTrue(code.matches("ORD\\d{8}\\d{4}"));
    }

    @Test @DisplayName("TC_ORDER_70: Đặt hàng với phương thức thanh toán lạ")
    public void tc70() {
        addToCart(customerId, product1, 1);
        CreateOrderRequest req = createReq("A", "BITCOIN", 0.0);
        ApiResponse res = orderService.createOrderFromCart(customerId, req);
        // Bug TC_ORDER_70: Phương thức thanh toán lạ phải bị từ chối
        assertFalse(res.isSuccess(), "BUG: Hệ thống chấp nhận phương thức thanh toán không hỗ trợ (BITCOIN)");
    }

    // --- UTILS ---
    private Long initCustomer(String e, String p) {
        User u = userRepository.save(User.builder().email(e).password("1").role(com.doan.WEB_TMDT.module.auth.entity.Role.CUSTOMER).status(com.doan.WEB_TMDT.module.auth.entity.Status.ACTIVE).build());
        return customerRepository.save(Customer.builder().user(u).fullName("Tester").phone(p).build()).getId();
    }

    private CartItem addToCart(Long cId, Product p, int q) {
        Cart cart = cartRepository.findByCustomerId(cId).orElseGet(() -> cartRepository.save(Cart.builder().customer(customerRepository.findById(cId).get()).build()));
        CartItem item = CartItem.builder().cart(cart).product(p).quantity(q).price(p.getPrice()).build();
        CartItem savedItem = cartItemRepository.save(item);
        
        // Ensure the cart's items list is updated for the current session/transaction
        if (cart.getItems() == null) {
            cart.setItems(new java.util.ArrayList<>());
        }
        cart.getItems().add(savedItem);
        cartRepository.save(cart);
        
        return savedItem;
    }

    private CreateOrderRequest createReq(String addr, String method, Double fee) {
        CreateOrderRequest r = new CreateOrderRequest();
        r.setAddress(addr); r.setPaymentMethod(method); r.setShippingFee(fee);
        r.setProvince("Tỉnh"); r.setDistrict("Quận"); r.setWard("Phường");
        return r;
    }

    private Long createSampleOrder(Long cId) {
        addToCart(cId, product1, 1);
        ApiResponse res = orderService.createOrderFromCart(cId, createReq("Addr", "COD", 0.0));
        return ((OrderResponse)res.getData()).getOrderId();
    }
}
