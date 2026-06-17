package com.cafekiosk.order.entity;

import com.cafekiosk.order.exception.InvalidOrderStatusTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 주문 상태 머신 단위 테스트.
 * Spring 컨텍스트 없이 순수 POJO 로 전이 규칙만 검증한다.
 */
class OrderTest {

    private Order newOrder() {
        Customer customer = new Customer("test@example.com");
        return new Order(customer, LocalDateTime.now(), "서울시 강남구", 12345);
    }

    @Test
    @DisplayName("신규 주문은 CONFIRMED 상태로 시작한다")
    void should_신규주문은_CONFIRMED로_시작한다() {
        Order order = newOrder();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("정상 흐름: CONFIRMED → IN_PROGRESS → READY → COMPLETED")
    void should_정상흐름_전이는_성공한다() {
        Order order = newOrder();

        order.startPreparing();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.IN_PROGRESS);

        order.markReady();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.READY);

        order.complete();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    @DisplayName("제조 시작은 CONFIRMED 상태에서만 가능하다")
    void should_제조시작은_확정상태에서만_가능하다() {
        Order order = newOrder();
        order.startPreparing(); // IN_PROGRESS

        assertThatThrownBy(order::startPreparing)
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    @Test
    @DisplayName("픽업 완료는 READY 상태에서만 가능하다 (CONFIRMED에서 바로 완료 불가)")
    void should_완료는_준비완료상태에서만_가능하다() {
        Order order = newOrder(); // CONFIRMED

        assertThatThrownBy(order::complete)
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    @Test
    @DisplayName("제조 시작 전(CONFIRMED)에는 취소할 수 있다")
    void should_확정상태에서_취소가능하다() {
        Order order = newOrder();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("제조 중(IN_PROGRESS)에도 취소할 수 있다")
    void should_제조중에도_취소가능하다() {
        Order order = newOrder();
        order.startPreparing();

        order.cancel();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("완료된 주문은 취소할 수 없다")
    void should_완료된주문은_취소할수없다() {
        Order order = newOrder();
        order.startPreparing();
        order.markReady();
        order.complete();

        assertThatThrownBy(order::cancel)
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }

    @Test
    @DisplayName("취소된 주문은 더 이상 전이할 수 없다")
    void should_취소된주문은_전이불가하다() {
        Order order = newOrder();
        order.cancel();

        assertThatThrownBy(order::startPreparing)
                .isInstanceOf(InvalidOrderStatusTransitionException.class);
    }
}
