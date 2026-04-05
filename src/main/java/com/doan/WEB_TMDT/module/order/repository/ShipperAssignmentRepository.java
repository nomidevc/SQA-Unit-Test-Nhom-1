package com.doan.WEB_TMDT.module.order.repository;

import com.doan.WEB_TMDT.module.order.entity.ShipperAssignment;
import com.doan.WEB_TMDT.module.order.entity.ShipperAssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShipperAssignmentRepository extends JpaRepository<ShipperAssignment, Long> {
    
    Optional<ShipperAssignment> findByOrderId(Long orderId);
    
    List<ShipperAssignment> findByShipperId(Long shipperId);
    
    List<ShipperAssignment> findByShipperIdAndStatus(Long shipperId, ShipperAssignmentStatus status);
    
    List<ShipperAssignment> findByStatus(ShipperAssignmentStatus status);
    
    @Query("SELECT sa FROM ShipperAssignment sa WHERE sa.shipper.id = :shipperId " +
           "AND sa.status IN ('CLAIMED', 'DELIVERING')")
    List<ShipperAssignment> findActiveAssignmentsByShipper(Long shipperId);
    
    boolean existsByOrderId(Long orderId);
}
