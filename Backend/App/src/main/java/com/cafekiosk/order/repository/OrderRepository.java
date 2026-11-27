package com.cafekiosk.order.repository;

import com.cafekiosk.order.entity.Order;
import com.cafekiosk.order.entity.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerEmail(String email);

    // 손님의 대기번호 조회. 인덱스를 따로 두지 않는 것은 order_number 의 유니크 제약이
    // 이미 인덱스를 만들기 때문이다. docs/ERD.md 5절
    Optional<Order> findByOrderNumber(String orderNumber);

    // 점주/주방용 목록. 주문 시각 오름차순(FIFO). 상태 필터 유무에 따라 둘 중 하나를 쓴다.
    List<Order> findByStatusOrderByOrderTimeAsc(OrderStatus status);

    List<Order> findAllByOrderByOrderTimeAsc();

}
