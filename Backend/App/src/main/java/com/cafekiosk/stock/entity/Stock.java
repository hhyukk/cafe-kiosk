package com.cafekiosk.stock.entity;

import com.cafekiosk.global.jpa.entity.BaseEntity;
import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.stock.exception.OutOfStockException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static jakarta.persistence.FetchType.LAZY;

/**
 * 재고 증감 규칙을 이 엔티티가 소유한다. 서비스는 조회해서 decrease 나 increase 를
 * 부르기만 하고 부족 판단을 자기가 하지 않는다. Order 가 상태 전이를 소유하는 것과 같은 원칙이다.
 *
 * quantity 에 CHECK 를 함께 거는 이유는 엔티티를 거치지 않는 경로가 언제나 생기기 때문이다.
 * 벌크 UPDATE, 네이티브 SQL, 운영자의 직접 조작이 그렇다. 엔티티가 1차 방어이고 CHECK 가 최후다.
 * 이 제약이 걸리는 날은 락이 새는 날이고, 그 사실이 조용히 넘어가지 않고 예외로 드러나는 것이 목적이다.
 */
@Entity
@Table(
        name = "stock",
        check = @CheckConstraint(name = "ck_stock_quantity_non_negative", constraint = "quantity >= 0")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock extends BaseEntity {

    /** 새로 등록한 메뉴가 갖는 재고 수량. */
    private static final int INITIAL_QUANTITY = 0;

    // 재고는 메뉴와 1:1
    @OneToOne(fetch = LAZY)
    @JoinColumn(name = "menu_id", unique = true)
    private Menu menu;

    private int quantity; // 현재 재고 수량

    public Stock(Menu menu, int quantity) {
        this.menu = menu;
        this.quantity = quantity;
    }

    /**
     * 새 메뉴의 재고 행을 만든다. 언제나 0 으로 시작한다.
     *
     * 등록 요청에서 수량을 받지 않는 이유는 점주가 등록 폼에 미리 적은 숫자가 실제 창고와
     * 맞을 이유가 없기 때문이다. 메뉴를 만드는 일과 팔 수 있게 만드는 일은 다른 일이고,
     * 뒤엣것은 재고 조정 하나가 맡는다. 그래서 새 메뉴는 품절로 태어난다.
     *
     * 0 을 MenuService 의 리터럴로 두지 않는 것은 그 숫자가 재고 도메인의 결정이라서다.
     * 메뉴 패키지가 재고 정책을 들고 있으면 초기 수량이 바뀌는 날 고칠 자리를 재고 쪽에서 찾게 된다.
     */
    public static Stock initialFor(Menu menu) {
        return new Stock(menu, INITIAL_QUANTITY);
    }

    /**
     * 재고를 차감한다. 부족하면 예외를 던지고 수량은 건드리지 않는다.
     * 부분 차감이 남지 않아야 주문 전체를 실패시키는 상위 규칙이 성립한다.
     */
    public void decrease(int amount) {
        requirePositive(amount);

        if (quantity < amount) {
            throw new OutOfStockException(menu.getId(), quantity, amount);
        }
        this.quantity -= amount;
    }

    /**
     * 재고를 복구하거나 늘린다. 상한을 두지 않는다.
     * 주문 취소로 되돌리는 경우는 차감이 선행됐으므로 원래 수량을 넘지 않고,
     * 점주가 재고를 조정하는 일은 별도 경로가 맡는다.
     */
    public void increase(int amount) {
        requirePositive(amount);

        this.quantity += amount;
    }

    // 0개를 깎거나 채우는 호출은 어느 경로에서도 의미가 없다.
    // 조용히 통과시키면 호출자가 수량을 잘못 계산한 사실이 묻힌다.
    private void requirePositive(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다: " + amount);
        }
    }
}
