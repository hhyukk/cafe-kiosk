package com.cafekiosk.stock.exception;

/**
 * 분산 락을 대기 한계 안에 얻지 못했을 때 발생한다.
 *
 * 손님이 요청을 고쳐 해결할 수 있는 종류는 아니지만 영구적인 상태도 아니다.
 * 그 재고 행 앞에 줄이 길어 순서를 못 받았을 뿐이고, 잠시 후 다시 누르면 된다.
 * GlobalExceptionHandler 가 503 으로 옮긴다. 재고 부족이나 상태 전이 충돌이 쓰는
 * 409 계열과 나누는 이유는, 저쪽이 자원의 현재 상태와 부딪힌 것인 반면
 * 이쪽은 자원에 닿아 보지도 못한 사건이기 때문이다.
 *
 * ── OutOfStockException 을 재사용하지 않는 이유 ──────────────────────────────
 *
 * 동시성 테스트가 실패수(OutOfStockException.class) 로 거절 건수를 센다.
 * 재고 3 에 10 스레드면 그 값이 정확히 7 이어야 락이 동작한다는 말을 할 수 있다.
 * 락 타임아웃이 같은 타입으로 섞이면 7 이 무엇의 7 인지 알 수 없게 되고,
 * ConcurrencyResult 가 총 실패 대신 타입별 실패를 세게 만든 이유가 통째로 사라진다.
 *
 * ── IllegalStateException 이 아닌 이유 ──────────────────────────────────────
 *
 * 던지는 자리가 리포지토리 프록시 안은 아니라 StockNotFoundException 이 걸렸던
 * 예외 변환 문제와는 무관하다. 다만 GlobalExceptionHandler 가 IllegalArgumentException 을
 * 400 으로 매핑하고 있어, 그 계열을 쓰면 손님 잘못이 아닌 사건이 400 으로 나간다.
 */
public class StockLockTimeoutException extends RuntimeException {

    public StockLockTimeoutException(Long menuId, long 대기_한계_초) {
        super("재고 락을 %d 초 안에 얻지 못했습니다: %d".formatted(대기_한계_초, menuId));
    }
}
