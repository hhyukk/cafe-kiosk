package com.cafekiosk.order.repository;

import com.cafekiosk.order.entity.Order;
import com.cafekiosk.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerEmail(String email);

    // 점주/주방용 목록 — 주문 시각 오름차순(FIFO). 상태 필터 유무에 따라 둘 중 하나를 쓴다.
    List<Order> findByStatusOrderByOrderTimeAsc(OrderStatus status);

    List<Order> findAllByOrderByOrderTimeAsc();

}
