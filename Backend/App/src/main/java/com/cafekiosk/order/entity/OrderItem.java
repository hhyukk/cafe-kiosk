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
     * 주문 시점의 메뉴 이름 스냅샷.
     * 이 값이 없으면 메뉴 이름을 수정할 때 과거 주문의 품목명이 소급 변경된다.
     *
     * menu_id 외래키는 그대로 남긴다. 어떤 메뉴가 얼마나 팔렸는지 집계하려면
     * 이름이 아니라 식별자로 묶어야 하기 때문이다. 이름은 바뀌어도 id 는 그대로다.
     */
    @Column(nullable = false)
    private String menuName;

    /**
     * 주문 시점의 메뉴 가격 스냅샷.
     * 이 값이 없으면 메뉴 가격을 수정할 때 과거 주문 금액이 소급 변경된다.
     */
    private int orderPrice;

    /**
     * 이름과 가격 두 스냅샷을 생성자가 메뉴에서 직접 복사한다.
     * 호출자에게 맡기면 언젠가 스냅샷을 빠뜨리는 경로가 생기기 때문이다.
     */
    public OrderItem(Order order, Menu menu, int count) {
        this.order = order;
        this.menu = menu;
        this.count = count;
        this.menuName = menu.getMenuName();
        this.orderPrice = menu.getMenuPrice();
    }

    /** 이 아이템의 소계. 주문 시점 가격 × 수량. */
    public int getSubtotal() {
        return orderPrice * count;
    }
}
