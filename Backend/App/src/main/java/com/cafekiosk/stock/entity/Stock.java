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

    /**
     * 낙관적 락이 읽는 버전이다. 이 필드 하나로 UPDATE 에 where version = ? 가 붙고,
     * 영향받은 행이 0 이면 Hibernate 가 커밋 시점에 충돌을 알린다.
     *
     * ── BaseEntity 가 아니라 여기에 붙인 이유 ─────────────────────────────────────
     *
     * 베이스에 두면 Menu 와 Order 와 OrderItem 과 Customer 가 전부 버전을 갖는다.
     * 이 프로젝트가 재려는 것은 재고 한 행을 놓고 벌어지는 경쟁인데, 거기에 주문 엔티티의
     * 버전 충돌이 섞이면 세 전략을 비교한 수치가 무엇을 잰 것인지 말할 수 없게 된다.
     * 낙관적 락이 필요한 행은 지금 이 행 하나뿐이다.
     *
     * ── 전략과 무관하게 언제나 켜져 있다 ─────────────────────────────────────────
     *
     * cafekiosk.stock.lock-strategy 가 pessimistic 이어도 이 컬럼은 살아 있고 UPDATE 에
     * 버전 조건이 붙는다. 그런데도 충돌하지 않는 것은 비관적 락이 행을 잠가 트랜잭션을
     * 줄 세우기 때문이다. 앞사람이 커밋한 뒤에야 뒷사람이 읽으므로 뒷사람이 들고 있는
     * 버전은 언제나 최신이다. 그래서 이 필드는 비관적 락 쪽 측정을 오염시키지 않는다.
     *
     * 다만 SQL 로그를 처음 보는 사람은 비관적 락인데 왜 버전 조건이 있느냐고 묻게 되므로
     * 여기 적어 둔다. 이 줄을 지우면 낙관적 락 전략이 아무것도 감지하지 못하는 채로
     * 조용히 통과한다. 락이 없던 시절과 같은 lost update 가 되고, 테스트만 초록이다.
     *
     * 타입은 래퍼가 아니라 원시 long 이다. 래퍼를 쓰면 null 이 아직 영속되지 않은 엔티티를
     * 뜻할 수 있지만, 여기는 PK 가 IDENTITY 라 Hibernate 가 식별자로 그 판별을 이미 한다.
     * docs/ERD.md 3-4 가 이 컬럼을 NOT NULL 기본값 0 으로 규정한 것과도 맞는다.
     */
    @Version
    private long version;

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
     * 점주가 재고를 조정하는 일은 아래 adjustTo 가 맡는다.
     */
    public void increase(int amount) {
        requirePositive(amount);

        this.quantity += amount;
    }

    /**
     * 재고를 센 값으로 맞춘다. 점주가 창고를 세고 그 숫자를 적는 일이다. FR-ADM-02.
     *
     * increase 와 decrease 로 표현하지 않는 이유는 이것이 증감이 아니기 때문이다.
     * 점주는 몇 개 늘었는지가 아니라 지금 몇 개인지를 안다. 델타로 받으면 화면이 현재 수량을
     * 읽어 빼서 보내야 하는데, 읽은 순간과 보내는 순간 사이에 손님이 주문하면 그 계산이 틀린다.
     * 절대값은 그 창을 없앤다.
     *
     * 0 을 허용한다. 품절 처리는 정상적인 운영 행위다. 그래서 increase 와 decrease 가 쓰는
     * requirePositive 를 여기서 재사용하지 않는다. 그쪽의 0 금지는 0 개를 깎거나 채우는
     * 호출이 의미가 없다는 규칙이고, 이쪽의 0 허용은 재고가 0 이 될 수 있다는 규칙이다.
     * 같은 숫자를 두고 정반대를 말하므로 검증도 따로 둔다.
     */
    public void adjustTo(int quantity) {
        requireNotNegative(quantity);

        this.quantity = quantity;
    }

    /**
     * 이 재고로 더 팔 수 있는지. 수량이 0 이면 품절이다. FR-KSK-08.
     *
     * 판정을 엔티티에 두는 이유는 decrease 가 부족을 스스로 판단하는 것과 같다.
     * 손님 화면이 stock === 0 을 직접 계산하기 시작하면 판정이 서버와 브라우저 두 곳에 생기고,
     * 나중에 최소 보유 수량 같은 규칙이 붙을 때 한쪽만 바뀐다.
     *
     * 음수를 같은 판정에 넣는 것은 방어다. CHECK 제약이 음수를 막지만
     * 그 제약이 뚫린 날 품절이 아니라고 말하는 쪽이 더 나쁘다.
     */
    public boolean isSoldOut() {
        return quantity <= 0;
    }

    // 0개를 깎거나 채우는 호출은 어느 경로에서도 의미가 없다.
    // 조용히 통과시키면 호출자가 수량을 잘못 계산한 사실이 묻힌다.
    private void requirePositive(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("수량은 1 이상이어야 합니다: " + amount);
        }
    }

    // 음수 재고는 어떤 경로로도 성립하지 않는다. FR-STK-06.
    // CHECK 제약이 최후에 막지만 거기까지 가면 500 이 나가므로 여기서 끊는다.
    private void requireNotNegative(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("재고 수량은 0 이상이어야 합니다: " + quantity);
        }
    }
}
