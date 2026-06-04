package com.cafekiosk.order.repository;

import com.cafekiosk.order.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    // 주문 → 고객 → 이메일을 통해 주문 상품 조회
    List<OrderItem> findByOrderCustomerEmail(String email);
}