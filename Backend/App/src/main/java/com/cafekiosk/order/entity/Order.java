package com.cafekiosk.order.entity;

import com.cafekiosk.global.jpa.entity.BaseEntity;
import com.cafekiosk.order.exception.InvalidOrderStatusTransitionException;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.cafekiosk.order.entity.OrderStatus.*;
import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor
public class Order extends BaseEntity {

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    private LocalDateTime orderTime;

    /** 손님이 받아가는 대기번호. 발급 규칙은 assignOrderNumber() 참고. */
    @Column(unique = true)
    private String orderNumber;

    /** 주문 시점 총액. orderItems 의 스냅샷 가격 합이며, addOrderItem() 으로만 늘어난다. */
    private int totalPrice;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    public Order(Customer customer, LocalDateTime orderTime) {
        setCustomer(customer);
        this.orderTime = orderTime;
        this.totalPrice = 0;
        // 장바구니 제출 단계가 없어 createOrder = 손님의 주문 확정 시점이므로 CONFIRMED 로 시작
        this.status = CONFIRMED;
    }


    public void setCustomer(Customer customer) {
        this.customer = customer;
        if (customer != null && !customer.getOrders().contains(this)) {
            customer.getOrders().add(this);
        }
    }

    /**
     * 주문 아이템을 추가하고 총액에 반영한다.
     * 총액을 서비스가 따로 더하지 않고 여기서만 늘리는 이유는,
     * totalPrice 가 orderItems 와 어긋날 수 있는 경로 자체를 없애기 위해서다.
     */
    public void addOrderItem(OrderItem orderItem) {
        orderItems.add(orderItem);
        this.totalPrice += orderItem.getSubtotal();
    }

    /**
     * 대기번호를 발급한다. PK 채번(INSERT) 이후에 호출해야 한다.
     *
     * 번호를 PK에서 파생시키는 이유: PK는 DB가 채번하므로(IDENTITY) 전역 유일하고 단조 증가한다.
     * "오늘 주문 수 + 1" 같은 방식은 조회와 삽입 사이에 경쟁이 생겨 번호가 겹칠 수 있는데,
     * 그건 Phase 2 의 재고 동시성에서 의도적으로 다룰 문제이지 대기번호에서 실수로 만들 문제가 아니다.
     */
    public void assignOrderNumber() {
        if (orderNumber != null) {
            throw new IllegalStateException("대기번호는 이미 발급되었습니다: " + orderNumber);
        }
        if (getId() == 0) {
            throw new IllegalStateException("PK 채번 전에는 대기번호를 발급할 수 없습니다.");
        }
        this.orderNumber = String.format("%04d", getId());
    }

    // - 상태 전이 메서드 -

    /** 점주 접수 → 제조 시작. CONFIRMED 에서만 가능. */
    public void startPreparing() {
        requireStatus(CONFIRMED, IN_PROGRESS);
        this.status = IN_PROGRESS;
    }

    /** 제조 완료 → 픽업 대기. IN_PROGRESS 에서만 가능. */
    public void markReady() {
        requireStatus(IN_PROGRESS, READY);
        this.status = READY;
    }

    /** 픽업 완료. READY 에서만 가능. */
    public void complete() {
        requireStatus(READY, COMPLETED);
        this.status = COMPLETED;
    }

    /** 점주 거부 또는 손님 취소. 제조 시작 전(CONFIRMED) 또는 제조 중(IN_PROGRESS)에서만 가능. */
    public void cancel() {
        if (status != CONFIRMED && status != IN_PROGRESS) {
            throw new InvalidOrderStatusTransitionException(status, CANCELLED);
        }
        this.status = CANCELLED;
    }

    private void requireStatus(OrderStatus expected, OrderStatus next) {
        if (status != expected) {
            throw new InvalidOrderStatusTransitionException(status, next);
        }
    }
}
