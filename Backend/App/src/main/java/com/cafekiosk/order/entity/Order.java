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
@AllArgsConstructor
public class Order extends BaseEntity {

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;
    
    private LocalDateTime orderTime;

    private String address;
    private int postcode;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    public Order(Customer customer, LocalDateTime orderTime, String address, int postcode) {
        setCustomer(customer);
        this.orderTime = orderTime;
        this.address = address;
        this.postcode = postcode;
        // 장바구니 제출 단계가 없어 createOrder = 손님의 주문 확정 시점이므로 CONFIRMED 로 시작
        this.status = CONFIRMED;
    }


    public void setCustomer(Customer customer) {
        this.customer = customer;
        if (customer != null && !customer.getOrders().contains(this)) {
            customer.getOrders().add(this);
        }
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
