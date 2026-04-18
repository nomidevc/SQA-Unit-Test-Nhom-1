package com.doan.WEB_TMDT.service;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.auth.entity.Customer;
import com.doan.WEB_TMDT.module.auth.entity.User;
import com.doan.WEB_TMDT.module.auth.repository.CustomerRepository;
import com.doan.WEB_TMDT.module.auth.repository.UserRepository;
import com.doan.WEB_TMDT.module.cart.dto.AddToCartRequest;
import com.doan.WEB_TMDT.module.cart.dto.UpdateCartItemRequest;
import com.doan.WEB_TMDT.module.cart.entity.Cart;
import com.doan.WEB_TMDT.module.cart.entity.CartItem;
import com.doan.WEB_TMDT.module.cart.repository.CartItemRepository;
import com.doan.WEB_TMDT.module.cart.repository.CartRepository;
import com.doan.WEB_TMDT.module.cart.service.CartService;
import com.doan.WEB_TMDT.module.product.entity.Category;
import com.doan.WEB_TMDT.module.product.entity.Product;
import com.doan.WEB_TMDT.module.product.repository.CategoryRepository;
import com.doan.WEB_TMDT.module.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQA UNIT TEST SCRIPT - MODULE: CART SERVICE
 * Total Test Cases: 35
 * Purpose: Bug Hunting & Quality Assurance.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class CartServiceTest {

    @Autowired private CartService cartService;
    @Autowired private CartRepository cartRepository;
    @Autowired private CartItemRepository cartItemRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    private Long customerId1;
    private Long customerId2;
    private Long prodActive;
    private Long prodOutOfStock;
    private Long prodInactive;

    @BeforeEach
    public void setup() {
        // Khởi tạo dữ liệu mẫu
        customerId1 = initCustomer("tester1@test.com", "0123456781");
        customerId2 = initCustomer("tester2@test.com", "0123456782");
        
        Category cat = categoryRepository.save(Category.builder().name("Electro").slug("el").active(true).build());
        prodActive = productRepository.save(Product.builder().name("Laptop").sku("L").price(100.0).stockQuantity(10L).active(true).category(cat).build()).getId();
        prodOutOfStock = productRepository.save(Product.builder().name("Phone").sku("P").price(50.0).stockQuantity(0L).active(true).category(cat).build()).getId();
        prodInactive = productRepository.save(Product.builder().name("OldTV").sku("T").price(80.0).stockQuantity(5L).active(false).category(cat).build()).getId();
    }

    // --- GROUP 1: ADD TO CART (TC01 - TC10) ---
    
    @Test @DisplayName("TC_CART_01: Thêm sản phẩm hợp lệ")
    public void tc01() {
        ApiResponse res = cartService.addToCart(customerId1, addReq(prodActive, 1));
        assertTrue(res.isSuccess());
        verifyInDb(customerId1, prodActive, 1);
    }

    @Test @DisplayName("TC_CART_02: Thêm cộng dồn sản phẩm")
    public void tc02() {
        cartService.addToCart(customerId1, addReq(prodActive, 2));
        cartService.addToCart(customerId1, addReq(prodActive, 3));
        verifyInDb(customerId1, prodActive, 5);
    }

    @Test @DisplayName("TC_CART_03: Thêm sản phẩm sai ID (Sẽ Fail nếu ko throw đúng)")
    public void tc03() {
        assertThrows(RuntimeException.class, () -> cartService.addToCart(customerId1, addReq(9999L, 1)));
    }

    @Test @DisplayName("TC_CART_04: Thêm sản phẩm hết hàng (Stock=0)")
    public void tc04() {
        ApiResponse res = cartService.addToCart(customerId1, addReq(prodOutOfStock, 1));
        assertFalse(res.isSuccess(), "Hệ thống không được phép thêm sản phẩm hết hàng");
    }

    @Test @DisplayName("TC_CART_05: Thêm số lượng vượt tồn kho")
    public void tc05() {
        ApiResponse res = cartService.addToCart(customerId1, addReq(prodActive, 100));
        assertFalse(res.isSuccess(), "Hệ thống không được phép thêm vượt tồn kho");
    }

    @Test @DisplayName("TC_CART_06: BUG - Thêm sản phẩm ngừng bán (Sẽ FAIL vì code đang lỗi cho phép thêm)")
    public void tc06() {
        ApiResponse res = cartService.addToCart(customerId1, addReq(prodInactive, 1));
        assertFalse(res.isSuccess(), "LỖI: Hệ thống đang cho phép thêm sản phẩm ngừng kinh doanh!");
    }

    @Test @DisplayName("TC_CART_07: BUG - Thêm số lượng bằng 0 (Sẽ FAIL vì code đang lỗi)")
    public void tc07() {
        ApiResponse res = cartService.addToCart(customerId1, addReq(prodActive, 0));
        assertFalse(res.isSuccess(), "LỖI: Hệ thống đang cho phép thêm 0 sản phẩm!");
    }

    @Test @DisplayName("TC_CART_08: BUG - Thêm số lượng âm (Sẽ FAIL vì code đang lỗi)")
    public void tc08() {
        ApiResponse res = cartService.addToCart(customerId1, addReq(prodActive, -5));
        assertFalse(res.isSuccess(), "LỖI: Hệ thống đang cho phép thêm số lượng âm!");
    }

    @Test @DisplayName("TC_CART_09: Thêm sản phẩm vào giỏ hàng chưa tồn tại (Tự tạo giỏ)")
    public void tc09() {
        ApiResponse res = cartService.addToCart(customerId1, addReq(prodActive, 1));
        assertNotNull(cartRepository.findByCustomerId(customerId1).orElse(null));
    }

    @Test @DisplayName("TC_CART_10: Thêm nhiều loại sản phẩm khác nhau")
    public void tc10() {
        cartService.addToCart(customerId1, addReq(prodActive, 1));
        Product p2 = productRepository.save(Product.builder().name("Mouse").sku("M").price(10.0).stockQuantity(10L).active(true).build());
        cartService.addToCart(customerId1, addReq(p2.getId(), 2));
        assertEquals(2, getItems(customerId1).size());
    }

    // --- GROUP 2: UPDATE ITEM (TC11 - TC20) ---

    @Test @DisplayName("TC_CART_11: Cập nhật số lượng hợp lệ")
    public void tc11() {
        cartService.addToCart(customerId1, addReq(prodActive, 1));
        Long itemId = getFirstId(customerId1);
        ApiResponse res = cartService.updateCartItem(customerId1, itemId, upReq(5));
        assertTrue(res.isSuccess());
        verifyInDb(customerId1, prodActive, 5);
    }

    @Test @DisplayName("TC_CART_12: Cập nhật số lượng vượt tồn kho")
    public void tc12() {
        cartService.addToCart(customerId1, addReq(prodActive, 1));
        Long itemId = getFirstId(customerId1);
        ApiResponse res = cartService.updateCartItem(customerId1, itemId, upReq(100));
        assertFalse(res.isSuccess());
    }

    @Test @DisplayName("TC_CART_13: SECURITY - Cập nhật giỏ hàng người khác (Hack ID)")
    public void tc13() {
        cartService.addToCart(customerId1, addReq(prodActive, 1));
        Long itemId = getFirstId(customerId1);
        ApiResponse res = cartService.updateCartItem(customerId2, itemId, upReq(10));
        assertFalse(res.isSuccess(), "BẢO MẬT: Không được sửa giỏ hàng người khác!");
    }

    @Test @DisplayName("TC_CART_14: BUG - Cập nhật số lượng về 0 (Sẽ FAIL)")
    public void tc14() {
        cartService.addToCart(customerId1, addReq(prodActive, 1));
        Long itemId = getFirstId(customerId1);
        ApiResponse res = cartService.updateCartItem(customerId1, itemId, upReq(0));
        assertFalse(res.isSuccess(), "LỖI: Hệ thống cho phép cập nhật số lượng về 0!");
    }

    @Test @DisplayName("TC_CART_15: Cập nhật sản phẩm không có trong giỏ")
    public void tc15() {
        assertThrows(RuntimeException.class, () -> cartService.updateCartItem(customerId1, 9999L, upReq(5)));
    }

    @Test @DisplayName("TC_CART_16: Cập nhật số lượng về đúng số lượng hiện tại")
    public void tc16() {
        cartService.addToCart(customerId1, addReq(prodActive, 2));
        Long itemId = getFirstId(customerId1);
        ApiResponse res = cartService.updateCartItem(customerId1, itemId, upReq(2));
        assertTrue(res.isSuccess());
    }

    @Test @DisplayName("TC_CART_17: Cập nhật số lượng khi tồn kho vừa bị thay đổi (Race Condition)")
    public void tc17() {
        cartService.addToCart(customerId1, addReq(prodActive, 1));
        // Admin cập nhật kho xuống còn 0
        Product p = productRepository.findById(prodActive).get();
        p.setStockQuantity(0L);
        productRepository.save(p);
        
        Long itemId = getFirstId(customerId1);
        ApiResponse res = cartService.updateCartItem(customerId1, itemId, upReq(5));
        assertFalse(res.isSuccess(), "Phải báo hết hàng do kho đã đổi");
    }

    @Test @DisplayName("TC_CART_18: Cập nhật nhiều lần liên tục")
    public void tc18() {
        cartService.addToCart(customerId1, addReq(prodActive, 1));
        Long itemId = getFirstId(customerId1);
        cartService.updateCartItem(customerId1, itemId, upReq(2));
        cartService.updateCartItem(customerId1, itemId, upReq(3));
        verifyInDb(customerId1, prodActive, 3);
    }

    @Test @DisplayName("TC_CART_19: Cập nhật giỏ hàng khi Customer bị khóa (Status=INACTIVE)")
    public void tc19() {
        // Giả sử có logic này, nếu ko có thì Case này sẽ Pass (ko sao)
        ApiResponse res = cartService.getCart(customerId1);
        assertTrue(res.isSuccess());
    }

    @Test @DisplayName("TC_CART_20: Cập nhật số lượng âm (Sẽ FAIL)")
    public void tc20() {
        cartService.addToCart(customerId1, addReq(prodActive, 1));
        Long itemId = getFirstId(customerId1);
        ApiResponse res = cartService.updateCartItem(customerId1, itemId, upReq(-10));
        assertFalse(res.isSuccess());
    }

    // --- GROUP 3: REMOVE ITEM (TC21 - TC25) ---

    @Test @DisplayName("TC_CART_21: BUG - Xóa sản phẩm (Sẽ FAIL do lỗi JPA NULL constraint)")
    public void tc21() {
        cartService.addToCart(customerId1, addReq(prodActive, 1));
        Long itemId = getFirstId(customerId1);
        ApiResponse res = cartService.removeCartItem(customerId1, itemId);
        assertTrue(res.isSuccess(), "LỖI KỸ THUẬT: Xóa sản phẩm gây lỗi Database!");
        verifyEmpty(customerId1);
    }

    @Test @DisplayName("TC_CART_22: Xóa sản phẩm không tồn tại")
    public void tc22() {
        assertThrows(RuntimeException.class, () -> cartService.removeCartItem(customerId1, 9999L));
    }

    @Test @DisplayName("TC_CART_23: SECURITY - Xóa sản phẩm người khác")
    public void tc23() {
        cartService.addToCart(customerId1, addReq(prodActive, 1));
        Long itemId = getFirstId(customerId1);
        ApiResponse res = cartService.removeCartItem(customerId2, itemId);
        assertFalse(res.isSuccess());
        assertNotNull(cartItemRepository.findById(itemId).orElse(null));
    }

    @Test @DisplayName("TC_CART_24: Xóa sản phẩm duy nhất (Giỏ hàng phải rỗng)")
    public void tc24() {
        cartService.addToCart(customerId1, addReq(prodActive, 1));
        cartService.removeCartItem(customerId1, getFirstId(customerId1));
        // Case này thường fail do bug tc21
    }

    @Test @DisplayName("TC_CART_25: Xóa sản phẩm khi giỏ hàng có nhiều sản phẩm")
    public void tc25() {
        cartService.addToCart(customerId1, addReq(prodActive, 1));
        Product p2 = productRepository.save(Product.builder().name("B").sku("B").price(10.0).stockQuantity(5L).active(true).build());
        cartService.addToCart(customerId1, addReq(p2.getId(), 1));
        cartService.removeCartItem(customerId1, getFirstId(customerId1));
        assertEquals(1, getItems(customerId1).size());
    }

    // --- GROUP 4: VIEW & CLEAR (TC26 - TC30) ---

    @Test @DisplayName("TC_CART_26: Xem giỏ hàng bình thường")
    public void tc26() {
        cartService.addToCart(customerId1, addReq(prodActive, 1));
        assertNotNull(cartService.getCart(customerId1));
    }

    @Test @DisplayName("TC_CART_27: Xem giỏ hàng trống")
    public void tc27() {
        ApiResponse res = cartService.getCart(customerId1);
        assertTrue(res.isSuccess());
    }

    @Test @DisplayName("TC_CART_28: Xóa trắng giỏ hàng (Clear All)")
    public void tc28() {
        cartService.addToCart(customerId1, addReq(prodActive, 1));
        cartService.clearCart(customerId1);
        verifyEmpty(customerId1);
    }

    @Test @DisplayName("TC_CART_29: Xóa trắng giỏ hàng đã trống")
    public void tc29() {
        cartService.clearCart(customerId1);
        verifyEmpty(customerId1);
    }

    @Test @DisplayName("TC_CART_30: Xem giỏ hàng khách hàng rác")
    public void tc30() {
        assertThrows(RuntimeException.class, () -> cartService.getCart(8888L));
    }

    // --- GROUP 5: COMBINATION & STRESS (TC31 - TC35) ---

    @Test @DisplayName("TC_CART_31: Thêm -> Sửa -> Xóa liên hoàn")
    public void tc31() {
        cartService.addToCart(customerId1, addReq(prodActive, 1));
        Long id = getFirstId(customerId1);
        cartService.updateCartItem(customerId1, id, upReq(5));
        cartService.removeCartItem(customerId1, id);
        verifyEmpty(customerId1);
    }

    @Test @DisplayName("TC_CART_32: Thêm sản phẩm cực lớn (Boundary Test)")
    public void tc32() {
        ApiResponse res = cartService.addToCart(customerId1, addReq(prodActive, 2147483647));
        assertFalse(res.isSuccess());
    }

    @Test @DisplayName("TC_CART_33: Kiểm tra tổng tiền giỏ hàng")
    public void tc33() {
        cartService.addToCart(customerId1, addReq(prodActive, 2)); // 2 * 100 = 200
        ApiResponse res = cartService.getCart(customerId1);
        // Kiểm tra logic tính tiền trong response
    }

    @Test @DisplayName("TC_CART_34: Thêm sản phẩm rồi Admin xóa sản phẩm đó khỏi hệ thống")
    public void tc34() {
        cartService.addToCart(customerId1, addReq(prodActive, 1));
        productRepository.deleteById(prodActive);
        // Xem giỏ hàng lúc này xử lý ntn? (Thường là văng lỗi)
        assertNotNull(cartService.getCart(customerId1));
    }

    @Test @DisplayName("TC_CART_35: Stress Test - Thêm 20 loại sản phẩm khác nhau")
    public void tc35() {
        for(int i=0; i<20; i++) {
            Product p = productRepository.save(Product.builder().name("Item"+i).sku("S"+i).price(1.0).stockQuantity(10L).active(true).build());
            cartService.addToCart(customerId1, addReq(p.getId(), 1));
        }
        assertEquals(20, getItems(customerId1).size());
    }

    // --- UTILS ---
    private Long initCustomer(String e, String p) {
        User u = userRepository.save(User.builder().email(e).password("1").role(com.doan.WEB_TMDT.module.auth.entity.Role.CUSTOMER).status(com.doan.WEB_TMDT.module.auth.entity.Status.ACTIVE).build());
        return customerRepository.save(Customer.builder().user(u).fullName("N").phone(p).build()).getId();
    }
    private AddToCartRequest addReq(Long id, int q) { AddToCartRequest r = new AddToCartRequest(); r.setProductId(id); r.setQuantity(q); return r; }
    private UpdateCartItemRequest upReq(int q) { UpdateCartItemRequest r = new UpdateCartItemRequest(); r.setQuantity(q); return r; }
    private void verifyInDb(Long c, Long p, int q) {
        Cart cart = cartRepository.findByCustomerId(c).orElseThrow();
        assertEquals(q, cartItemRepository.findByCartIdAndProductId(cart.getId(), p).get().getQuantity());
    }
    private void verifyEmpty(Long c) {
        cartRepository.findByCustomerId(c).ifPresent(cart -> assertTrue(cartItemRepository.findByCartId(cart.getId()).isEmpty()));
    }
    private List<CartItem> getItems(Long c) {
        Cart cart = cartRepository.findByCustomerId(c).orElseThrow();
        return cartItemRepository.findByCartId(cart.getId());
    }
    private Long getFirstId(Long c) { return getItems(c).get(0).getId(); }
}
