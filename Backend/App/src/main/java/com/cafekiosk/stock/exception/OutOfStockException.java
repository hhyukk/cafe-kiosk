package com.cafekiosk.stock.exception;

/**
 * 남은 재고보다 많이 차감하려 했을 때 발생한다.
 *
 * 메뉴 이름 대신 menuId 를 담는다. Stock 의 menu 연관은 LAZY 라서 이름을 읽는 순간
 * 프록시가 초기화되며 SELECT 가 한 번 더 나간다. 재고 부족은 동시성 테스트에서
 * 스레드마다 터지는 경로이고, 거기에 조회를 얹으면 재려던 경쟁 구간 자체가 달라진다.
 * 프록시의 식별자 접근은 초기화를 일으키지 않는다.
 */
public class OutOfStockException extends RuntimeException {

    public OutOfStockException(long menuId, int available, int requested) {
        super("재고가 부족합니다. 메뉴 " + menuId + ", 남은 수량 " + available + ", 요청 수량 " + requested);
    }
}
