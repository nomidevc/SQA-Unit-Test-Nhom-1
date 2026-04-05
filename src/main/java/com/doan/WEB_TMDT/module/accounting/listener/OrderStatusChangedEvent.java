package com.doan.WEB_TMDT.module.accounting.listener;

import com.doan.WEB_TMDT.module.order.entity.Order;
import com.doan.WEB_TMDT.module.order.entity.OrderStatus;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event được publish khi trạng thái đơn hàng thay đổi
 */
@Getter
public class OrderStatusChangedEvent extends ApplicationEvent {
    
    private final Order order;
    private final OrderStatus oldStatus;
    private final OrderStatus newStatus;
    
    public OrderStatusChangedEvent(Object source, Order order, OrderStatus oldStatus, OrderStatus newStatus) {
        super(source);
        this.order = order;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
    }
}
