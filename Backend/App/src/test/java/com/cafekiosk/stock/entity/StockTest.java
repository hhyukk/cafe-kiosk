package com.cafekiosk.stock.entity;

import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.stock.exception.OutOfStockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 재고 증감 단위 테스트.
 * Spring 컨텍스트 없이 순수 POJO 로 경계값만 검증한다.
 */
class StockTest {

    private Stock newStock(int quantity) {
        Menu menu = new Menu("브라질 산토스", "/uploads/Brazil Santos.jpg", 12000, "커피원두", "test@example.com");
        return new Stock(menu, quantity);
    }

    @Test
    @DisplayName("남은 재고보다 적게 차감하면 그만큼 줄어든다")
    void should_재고보다_적게_깎으면_줄어든다() {
        Stock stock = newStock(3);

        stock.decrease(1);

        assertThat(stock.getQuantity()).isEqualTo(2);
    }

    @Test
    @DisplayName("남은 재고와 같은 수량은 차감할 수 있고 0이 된다")
    void should_재고와_같은_수량은_깎을수있다() {
        Stock stock = newStock(3);

        stock.decrease(3);

        assertThat(stock.getQuantity()).isZero();
    }

    @Test
    @DisplayName("남은 재고보다 많이 차감하려 하면 예외가 나고 수량은 그대로다")
    void should_재고보다_많이_깎으면_예외이고_수량은_그대로다() {
        Stock stock = newStock(3);

        assertThatThrownBy(() -> stock.decrease(4))
                .isInstanceOf(OutOfStockException.class);

        // 부분 차감이 남으면 주문 전체를 실패시키는 상위 규칙이 무너진다
        assertThat(stock.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("재고가 0이면 1개도 차감할 수 없다")
    void should_재고가0이면_차감할수없다() {
        Stock stock = newStock(0);

        assertThatThrownBy(() -> stock.decrease(1))
                .isInstanceOf(OutOfStockException.class);

        assertThat(stock.getQuantity()).isZero();
    }

    @Test
    @DisplayName("0개 차감은 허용하지 않는다")
    void should_0개_차감은_예외다() {
        Stock stock = newStock(3);

        assertThatThrownBy(() -> stock.decrease(0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(stock.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("음수 차감으로 재고를 늘리는 우회 경로를 막는다")
    void should_음수_차감은_예외다() {
        Stock stock = newStock(3);

        assertThatThrownBy(() -> stock.decrease(-5))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(stock.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("재고를 늘리면 그만큼 증가한다")
    void should_재고를_늘리면_증가한다() {
        Stock stock = newStock(3);

        stock.increase(2);

        assertThat(stock.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("0개 복구는 허용하지 않는다")
    void should_0개_복구는_예외다() {
        Stock stock = newStock(3);

        assertThatThrownBy(() -> stock.increase(0))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(stock.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("음수 복구로 재고를 깎는 우회 경로를 막는다")
    void should_음수_복구는_예외다() {
        Stock stock = newStock(3);

        assertThatThrownBy(() -> stock.increase(-1))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(stock.getQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("차감한 만큼 복구하면 원래 수량으로 돌아온다")
    void should_차감후_복구하면_원래대로_돌아온다() {
        Stock stock = newStock(3);

        stock.decrease(3);
        stock.increase(3);

        assertThat(stock.getQuantity()).isEqualTo(3);
    }
}
