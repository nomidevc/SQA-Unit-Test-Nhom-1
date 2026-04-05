package com.doan.WEB_TMDT.module.auth.controller;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.auth.entity.Customer;
import com.doan.WEB_TMDT.module.auth.entity.User;
import com.doan.WEB_TMDT.module.auth.repository.CustomerRepository;
import com.doan.WEB_TMDT.module.auth.repository.UserRepository;
import com.doan.WEB_TMDT.module.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;

    /**
     * Lấy danh sách tất cả khách hàng (cho nhân viên/admin)
     */
    @GetMapping("/all")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'EMPLOYEE', 'SALE', 'WAREHOUSE', 'SHIPPER', 'PRODUCT_MANAGER', 'ACCOUNTANT', 'CSKH')")
    public ApiResponse getAllCustomers() {
        try {
            List<Customer> customers = customerRepository.findAll();
            
            List<Map<String, Object>> result = customers.stream().map(customer -> {
                Map<String, Object> data = new HashMap<>();
                data.put("id", customer.getId());
                data.put("fullName", customer.getFullName());
                data.put("phone", customer.getPhone());
                data.put("address", customer.getAddress());
                data.put("gender", customer.getGender());
                data.put("birthDate", customer.getBirthDate());
                
                // Lấy email từ User
                if (customer.getUser() != null) {
                    data.put("email", customer.getUser().getEmail());
                }
                
                // Đếm số đơn hàng
                long orderCount = orderRepository.countByCustomerId(customer.getId());
                data.put("orderCount", orderCount);
                
                return data;
            }).collect(Collectors.toList());
            
            return ApiResponse.success("Danh sách khách hàng", result);
        } catch (Exception e) {
            return ApiResponse.error("Lỗi: " + e.getMessage());
        }
    }

    /**
     * Lấy thông tin profile của customer đang đăng nhập
     */
    @GetMapping("/profile")
    @PreAuthorize("hasAnyAuthority('CUSTOMER', 'ADMIN')")
    public ApiResponse getProfile(Authentication authentication) {
        try {
            String email = authentication.getName();
            
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
            
            Customer customer = customerRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin khách hàng"));
            
            return ApiResponse.success("Thông tin khách hàng", customer);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * Cập nhật thông tin profile
     */
    @PutMapping("/profile")
    public ApiResponse updateProfile(@RequestBody Customer updateData, Authentication authentication) {
        try {
            String email = authentication.getName();
            
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
            
            Customer customer = customerRepository.findByUser(user)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin khách hàng"));
            
            // Cập nhật thông tin
            if (updateData.getFullName() != null) {
                customer.setFullName(updateData.getFullName());
            }
            if (updateData.getPhone() != null) {
                customer.setPhone(updateData.getPhone());
            }
            if (updateData.getAddress() != null) {
                customer.setAddress(updateData.getAddress());
            }
            if (updateData.getGender() != null) {
                customer.setGender(updateData.getGender());
            }
            if (updateData.getBirthDate() != null) {
                customer.setBirthDate(updateData.getBirthDate());
            }
            
            customerRepository.save(customer);
            
            return ApiResponse.success("Cập nhật thông tin thành công", customer);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }
}
