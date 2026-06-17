package com.cafekiosk.order.entity;

/**
 * 주문 상태 머신.
 * <p>
 * CONFIRMED → IN_PROGRESS → READY → COMPLETED 가 정상 흐름이며,
 * 제조 시작(IN_PROGRESS) 전까지는 CANCELLED 로 빠질 수 있다.
 * PENDING 은 장바구니 미제출 단계를 위한 예약 값으로, 현재 서버 흐름에서는 사용하지 않는다.
 * <p>
 * 실제 전이 가능 여부 검증은 {@link Order} 의 전이 메서드에서 수행한다.
 */
public enum OrderStatus {
    PENDING,      // (예약) 장바구니 미제출 — 현재 서버에서는 진입하지 않음
    CONFIRMED,    // 손님 주문 확정 — 서버에 최초 영속되는 상태
    IN_PROGRESS,  // 점주 접수 후 제조 중
    READY,        // 제조 완료, 픽업 대기
    COMPLETED,    // 픽업 완료
    CANCELLED     // 점주 거부 또는 손님 취소
}
