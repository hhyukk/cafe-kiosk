package com.cafekiosk.stock.entity;

import com.cafekiosk.global.jpa.entity.BaseEntity;
import com.cafekiosk.menu.entity.Menu;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static jakarta.persistence.FetchType.LAZY;

@Entity
@Table(name = "stock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock extends BaseEntity {

    // 재고는 메뉴와 1:1
    @OneToOne(fetch = LAZY)
    @JoinColumn(name = "menu_id", unique = true)
    private Menu menu;

    private int quantity; // 현재 재고 수량

    public Stock(Menu menu, int quantity) {
        this.menu = menu;
        this.quantity = quantity;
    }
}
