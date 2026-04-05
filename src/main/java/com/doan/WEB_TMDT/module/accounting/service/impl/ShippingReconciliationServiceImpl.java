package com.doan.WEB_TMDT.module.accounting.service.impl;

import com.doan.WEB_TMDT.module.accounting.dto.ShippingReconciliationResponse;
import com.doan.WEB_TMDT.module.accounting.service.ShippingReconciliationService;
import com.doan.WEB_TMDT.module.order.entity.Order;
import com.doan.WEB_TMDT.module.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShippingReconciliationServiceImpl implements ShippingReconciliationService {
    
    private final OrderRepository orderRepository;
    
    @Override
    public ShippingReconciliationResponse generateReconciliation(String startDate, String endDate) {
        LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
        LocalDateTime end = LocalDate.parse(endDate).atTime(23, 59, 59);
        
        // Get all paid orders in date range
        List<Order> orders = orderRepository.findPaidOrdersBetween(start, end);
        
        List<ShippingReconciliationResponse.ShippingDetail> details = new ArrayList<>();
        double totalFeeCollected = 0.0;
        double totalCostPaid = 0.0;
        
        for (Order order : orders) {
            // Shipping fee collected from customer
            Double shippingFee = order.getShippingFee() != null ? order.getShippingFee() : 0.0;
            
            // Actual shipping cost (80% of collected fee as per business rule)
            Double actualCost = shippingFee * 0.8;
            
            // Profit
            Double profit = shippingFee - actualCost;
            
            totalFeeCollected += shippingFee;
            totalCostPaid += actualCost;
            
            // Build shipping address
            String address = buildShippingAddress(order);
            
            details.add(ShippingReconciliationResponse.ShippingDetail.builder()
                .orderId(order.getId())
                .orderDate(order.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .shippingAddress(address)
                .shippingFeeCollected(shippingFee)
                .actualShippingCost(actualCost)
                .profit(profit)
                .build());
        }
        
        double totalProfit = totalFeeCollected - totalCostPaid;
        double profitMargin = totalFeeCollected > 0 ? (totalProfit / totalFeeCollected) * 100 : 0.0;
        
        String period = formatPeriod(startDate, endDate);
        
        return ShippingReconciliationResponse.builder()
            .period(period)
            .totalOrders(orders.size())
            .totalShippingFeeCollected(totalFeeCollected)
            .totalShippingCostPaid(totalCostPaid)
            .shippingProfit(totalProfit)
            .profitMargin(profitMargin)
            .details(details)
            .build();
    }
    
    private String buildShippingAddress(Order order) {
        StringBuilder address = new StringBuilder();
        
        if (order.getShippingAddress() != null && !order.getShippingAddress().isEmpty()) {
            address.append(order.getShippingAddress());
        }
        
        if (order.getWardName() != null && !order.getWardName().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(order.getWardName());
        }
        
        if (order.getDistrict() != null && !order.getDistrict().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(order.getDistrict());
        }
        
        if (order.getProvince() != null && !order.getProvince().isEmpty()) {
            if (address.length() > 0) address.append(", ");
            address.append(order.getProvince());
        }
        
        return address.toString();
    }
    
    private String formatPeriod(String startDate, String endDate) {
        DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        
        LocalDate start = LocalDate.parse(startDate, inputFormatter);
        LocalDate end = LocalDate.parse(endDate, inputFormatter);
        
        return start.format(outputFormatter) + " - " + end.format(outputFormatter);
    }
}
