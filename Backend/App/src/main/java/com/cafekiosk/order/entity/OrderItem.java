package com.cafekiosk.order.entity;

import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.global.jpa.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "order_item")
@Getter
@NoArgsConstructor
public class OrderItem extends BaseEntity {

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "menu_id")
    private Menu menu;

    private int count;

    /**
     * 주문 시점의 메뉴 가격 스냅샷.
     * 이 값이 없으면 메뉴 가격을 수정할 때 과거 주문 금액이 소급 변경된다.
     */
    private int orderPrice;

    /**
     * 가격은 생성자가 메뉴에서 직접 복사한다.
     * 호출자에게 맡기면 언젠가 스냅샷을 빠뜨리는 경로가 생기기 때문이다.
     */
    public OrderItem(Order order, Menu menu, int count) {
        this.order = order;
        this.menu = menu;
        this.count = count;
        this.orderPrice = menu.getMenuPrice();
    }

    /** 이 아이템의 소계 — 주문 시점 가격 × 수량. */
    public int getSubtotal() {
        return orderPrice * count;
    }
}
