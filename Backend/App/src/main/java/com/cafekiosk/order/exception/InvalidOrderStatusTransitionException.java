package com.cafekiosk.order.exception;

import com.cafekiosk.order.entity.OrderStatus;

/**
 * 허용되지 않은 주문 상태 전이를 시도했을 때 발생한다.
 * (예: COMPLETED → IN_PROGRESS)
 */
public class InvalidOrderStatusTransitionException extends RuntimeException {

    public InvalidOrderStatusTransitionException(OrderStatus current, OrderStatus next) {
        super("허용되지 않은 주문 상태 전이입니다: " + current + " → " + next);
    }
}
