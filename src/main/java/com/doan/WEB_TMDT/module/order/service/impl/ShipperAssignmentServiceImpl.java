package com.doan.WEB_TMDT.module.order.service.impl;

import com.doan.WEB_TMDT.common.dto.ApiResponse;
import com.doan.WEB_TMDT.module.auth.entity.Employee;
import com.doan.WEB_TMDT.module.auth.entity.Position;
import com.doan.WEB_TMDT.module.auth.repository.EmployeeRepository;
import com.doan.WEB_TMDT.module.order.dto.ShipperAssignmentRequest;
import com.doan.WEB_TMDT.module.order.dto.ShipperAssignmentResponse;
import com.doan.WEB_TMDT.module.order.entity.Order;
import com.doan.WEB_TMDT.module.order.entity.OrderStatus;
import com.doan.WEB_TMDT.module.order.entity.ShipperAssignment;
import com.doan.WEB_TMDT.module.order.entity.ShipperAssignmentStatus;
import com.doan.WEB_TMDT.module.order.repository.OrderRepository;
import com.doan.WEB_TMDT.module.order.repository.ShipperAssignmentRepository;
import com.doan.WEB_TMDT.module.order.service.ShipperAssignmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipperAssignmentServiceImpl implements ShipperAssignmentService {
    
    private final ShipperAssignmentRepository assignmentRepository;
    private final OrderRepository orderRepository;
    private final EmployeeRepository employeeRepository;
    
    @Override
    public ApiResponse getAvailableOrdersForShipper() {
        // Lấy các đơn hàng:
        // - Trạng thái READY_TO_SHIP
        // - Chưa có GHN order code
        // - Trong nội thành Hà Nội
        // - Chưa có shipper nhận
        
        List<Order> orders = orderRepository.findAll().stream()
                .filter(order -> order.getStatus() == OrderStatus.READY_TO_SHIP)
                .filter(order -> order.getGhnOrderCode() == null || order.getGhnOrderCode().isEmpty())
                .filter(this::isHanoiInnerCity)
                .filter(order -> !assignmentRepository.existsByOrderId(order.getId()))
                .collect(Collectors.toList());
        
        return ApiResponse.success("Danh sách đơn hàng có thể nhận", orders);
    }
    
    @Override
    @Transactional
    public ApiResponse claimOrder(Long orderId, Long shipperId) {
        try {
            // Kiểm tra đơn hàng
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));
            
            // Kiểm tra đơn hàng phải ở trạng thái READY_TO_SHIP
            if (order.getStatus() != OrderStatus.READY_TO_SHIP) {
                return ApiResponse.error("Chỉ có thể nhận đơn hàng ở trạng thái 'Đã chuẩn bị hàng'");
            }
            
            // Kiểm tra đơn hàng chưa có GHN order code
            if (order.getGhnOrderCode() != null && !order.getGhnOrderCode().isEmpty()) {
                return ApiResponse.error("Đơn hàng này đã sử dụng Giao Hàng Nhanh");
            }
            
            // Kiểm tra đơn hàng phải trong nội thành Hà Nội
            if (!isHanoiInnerCity(order)) {
                return ApiResponse.error("Chỉ có thể nhận đơn hàng trong nội thành Hà Nội");
            }
            
            // Kiểm tra đã có shipper nhận chưa (race condition check)
            if (assignmentRepository.existsByOrderId(order.getId())) {
                return ApiResponse.error("Đơn hàng này đã có shipper khác nhận rồi");
            }
            
            // Kiểm tra shipper
            Employee shipper = employeeRepository.findById(shipperId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên"));
            
            if (shipper.getPosition() != Position.SHIPPER) {
                return ApiResponse.error("Bạn không phải là shipper");
            }
            
            // Tạo assignment - nhận đơn = đang giao luôn
            LocalDateTime now = LocalDateTime.now();
            ShipperAssignment assignment = ShipperAssignment.builder()
                    .order(order)
                    .shipper(shipper)
                    .status(ShipperAssignmentStatus.DELIVERING) // Đang giao luôn
                    .assignedAt(now)
                    .claimedAt(now)
                    .deliveringAt(now) // Set thời gian bắt đầu giao
                    .build();
            
            assignmentRepository.save(assignment);
            
            // Cập nhật trạng thái đơn hàng sang SHIPPING
            order.setStatus(OrderStatus.SHIPPING);
            order.setShippedAt(now);
            orderRepository.save(order);
            
            log.info("Shipper {} claimed and started delivering order {}", shipper.getFullName(), order.getOrderCode());
            
            return ApiResponse.success("Đã nhận đơn và bắt đầu giao hàng", toResponse(assignment));
            
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            // Xử lý trường hợp unique constraint violation (2 shipper nhận cùng lúc)
            log.warn("Shipper {} tried to claim order {} but it was already claimed", shipperId, orderId);
            return ApiResponse.error("Đơn hàng này đã có shipper khác nhận rồi. Vui lòng chọn đơn khác.");
        } catch (jakarta.persistence.OptimisticLockException e) {
            // Xử lý optimistic locking exception
            log.warn("Optimistic lock exception when shipper {} tried to claim order {}", shipperId, orderId);
            return ApiResponse.error("Đơn hàng này đã có shipper khác nhận rồi. Vui lòng chọn đơn khác.");
        }
    }
    
    @Override
    public ApiResponse getMyOrders(Long shipperId) {
        List<ShipperAssignment> assignments = assignmentRepository.findByShipperId(shipperId);
        List<ShipperAssignmentResponse> responses = assignments.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ApiResponse.success("Danh sách đơn hàng của bạn", responses);
    }
    
    @Override
    public ApiResponse getMyActiveOrders(Long shipperId) {
        List<ShipperAssignment> assignments = assignmentRepository.findActiveAssignmentsByShipper(shipperId);
        List<ShipperAssignmentResponse> responses = assignments.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ApiResponse.success("Danh sách đơn hàng đang giao", responses);
    }
    
    @Override
    @Transactional
    public ApiResponse startDelivery(Long assignmentId, Long shipperId) {
        ShipperAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phân công"));
        
        if (!assignment.getShipper().getId().equals(shipperId)) {
            return ApiResponse.error("Bạn không có quyền thao tác với đơn hàng này");
        }
        
        if (assignment.getStatus() != ShipperAssignmentStatus.CLAIMED) {
            return ApiResponse.error("Đơn hàng không ở trạng thái đã nhận");
        }
        
        assignment.setStatus(ShipperAssignmentStatus.DELIVERING);
        assignment.setDeliveringAt(LocalDateTime.now());
        assignmentRepository.save(assignment);
        
        // Cập nhật trạng thái đơn hàng sang SHIPPING
        Order order = assignment.getOrder();
        order.setStatus(OrderStatus.SHIPPING);
        order.setShippedAt(LocalDateTime.now());
        orderRepository.save(order);
        
        log.info("Shipper {} started delivering order {}", shipperId, order.getOrderCode());
        
        return ApiResponse.success("Đã lấy hàng và bắt đầu giao", toResponse(assignment));
    }
    
    @Override
    @Transactional
    public ApiResponse cancelClaim(Long assignmentId, Long shipperId) {
        ShipperAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phân công"));
        
        if (!assignment.getShipper().getId().equals(shipperId)) {
            return ApiResponse.error("Bạn không có quyền thao tác với đơn hàng này");
        }
        
        if (assignment.getStatus() != ShipperAssignmentStatus.CLAIMED) {
            return ApiResponse.error("Chỉ có thể hủy khi đơn hàng ở trạng thái đã nhận (chưa lấy hàng)");
        }
        
        assignment.setStatus(ShipperAssignmentStatus.CANCELLED);
        assignmentRepository.save(assignment);
        
        log.info("Shipper {} cancelled claim for order {}", shipperId, assignment.getOrder().getOrderCode());
        
        return ApiResponse.success("Đã hủy nhận đơn hàng");
    }
    
    @Override
    @Transactional
    public ApiResponse confirmDelivery(Long assignmentId, Long shipperId) {
        ShipperAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phân công"));
        
        if (!assignment.getShipper().getId().equals(shipperId)) {
            return ApiResponse.error("Bạn không có quyền thao tác với đơn hàng này");
        }
        
        if (assignment.getStatus() != ShipperAssignmentStatus.DELIVERING) {
            return ApiResponse.error("Đơn hàng chưa ở trạng thái đang giao");
        }
        
        assignment.setStatus(ShipperAssignmentStatus.DELIVERED);
        assignment.setDeliveredAt(LocalDateTime.now());
        assignmentRepository.save(assignment);
        
        // Cập nhật trạng thái đơn hàng sang DELIVERED
        Order order = assignment.getOrder();
        order.setStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        orderRepository.save(order);
        
        log.info("Shipper {} delivered order {}", shipperId, order.getOrderCode());
        
        return ApiResponse.success("Đã giao hàng thành công", toResponse(assignment));
    }
    
    @Override
    @Transactional
    public ApiResponse reportFailure(Long assignmentId, Long shipperId, String reason) {
        ShipperAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phân công"));
        
        if (!assignment.getShipper().getId().equals(shipperId)) {
            return ApiResponse.error("Bạn không có quyền thao tác với đơn hàng này");
        }
        
        if (assignment.getStatus() != ShipperAssignmentStatus.DELIVERING) {
            return ApiResponse.error("Chỉ có thể báo thất bại khi đang giao hàng");
        }
        
        assignment.setStatus(ShipperAssignmentStatus.FAILED);
        assignment.setFailedAt(LocalDateTime.now());
        assignment.setFailureReason(reason);
        assignmentRepository.save(assignment);
        
        log.warn("Shipper {} reported delivery failure for order {}: {}", 
                shipperId, assignment.getOrder().getOrderCode(), reason);
        
        return ApiResponse.success("Đã báo giao hàng thất bại", toResponse(assignment));
    }
    
    @Override
    public ApiResponse getAssignmentDetail(Long assignmentId) {
        ShipperAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phân công"));
        return ApiResponse.success("Chi tiết phân công", toResponse(assignment));
    }
    
    @Override
    @Transactional
    public ApiResponse cancelAssignment(Long orderId) {
        assignmentRepository.findByOrderId(orderId).ifPresent(assignment -> {
            assignment.setStatus(ShipperAssignmentStatus.CANCELLED);
            assignmentRepository.save(assignment);
            log.info("Cancelled shipper assignment for order {}", orderId);
        });
        return ApiResponse.success("Đã hủy phân công shipper");
    }
    
    @Override
    public ApiResponse getAllAssignments() {
        List<ShipperAssignment> assignments = assignmentRepository.findAll();
        List<ShipperAssignmentResponse> responses = assignments.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
        return ApiResponse.success("Danh sách tất cả phân công", responses);
    }
    
    @Override
    public ApiResponse getAssignmentByOrder(Long orderId) {
        return assignmentRepository.findByOrderId(orderId)
                .map(assignment -> ApiResponse.success("Chi tiết phân công", toResponse(assignment)))
                .orElse(ApiResponse.success("Chưa có shipper nhận đơn này", null));
    }
    
    // Helper methods
    
    private boolean isHanoiInnerCity(Order order) {
        // Kiểm tra đơn hàng có trong nội thành Hà Nội không
        if (order.getProvince() == null || order.getDistrict() == null) {
            return false;
        }
        
        String province = order.getProvince().toLowerCase().trim();
        String district = order.getDistrict().toLowerCase().trim();
        
        // Kiểm tra có phải Hà Nội không (chặt chẽ hơn)
        boolean isHanoi = province.contains("hà nội") || 
                         province.contains("ha noi") || 
                         province.equals("hanoi");
        
        if (!isHanoi) {
            return false;
        }
        
        // Danh sách các quận nội thành Hà Nội (chính xác)
        String[] innerDistricts = {
            "ba đình", "ba dinh",
            "hoàn kiếm", "hoan kiem", 
            "tây hồ", "tay ho",
            "long biên", "long bien",
            "cầu giấy", "cau giay",
            "đống đa", "dong da",
            "hai bà trưng", "hai ba trung",
            "hoàng mai",
            "thanh xuân", "thanh xuan",
            "nam từ liêm", "nam tu liem",
            "bắc từ liêm", "bac tu liem",
            "hà đông", "ha dong"
        };
        
        // Kiểm tra district có chứa từ "quận" hoặc nằm trong danh sách nội thành
        for (String innerDistrict : innerDistricts) {
            if (district.contains(innerDistrict)) {
                return true;
            }
        }
        
        // Nếu có từ "quận" thì cũng coi là nội thành
        if (district.contains("quận") || district.contains("quan")) {
            return true;
        }
        
        return false;
    }
    
    private ShipperAssignmentResponse toResponse(ShipperAssignment assignment) {
        Order order = assignment.getOrder();
        Employee shipper = assignment.getShipper();
        
        return ShipperAssignmentResponse.builder()
                .id(assignment.getId())
                .orderId(order.getId())
                .orderCode(order.getOrderCode())
                .shipperId(shipper.getId())
                .shipperName(shipper.getFullName())
                .shipperPhone(shipper.getPhone())
                .status(assignment.getStatus())
                .claimedAt(assignment.getClaimedAt())
                .deliveringAt(assignment.getDeliveringAt())
                .deliveredAt(assignment.getDeliveredAt())
                .failedAt(assignment.getFailedAt())
                .failureReason(assignment.getFailureReason())
                .shipperNote(assignment.getShipperNote())
                .customerName(order.getCustomer().getFullName())
                .customerPhone(order.getCustomer().getPhone())
                .shippingAddress(order.getShippingAddress())
                .total(order.getTotal())
                .build();
    }
}
