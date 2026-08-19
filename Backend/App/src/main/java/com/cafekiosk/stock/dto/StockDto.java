package com.cafekiosk.stock.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public class StockDto {

    /**
     * 점주의 재고 조정 요청. 증감이 아니라 절대 수량이다.
     *
     * quantity 가 원시 int 가 아니라 Integer 인 이유는 OrderDto.OrderItemRequest 의 count 와
     * 반대다. 그쪽은 0 이 잘못된 값이라 @Positive 하나로 걸리지만 여기서는 0 이 정상 값이다.
     * int 로 두면 필드를 아예 빠뜨린 요청이 0 으로 채워져 품절 처리로 조용히 성립한다.
     * Integer 와 @NotNull 이라야 누락이 400 이 된다.
     *
     * 음수 검증을 여기와 Stock.adjustTo 양쪽에 두는 것은 중복이 아니라 두 겹이다.
     * 이쪽은 HTTP 요청에 대한 1차 방어이고, 엔티티 쪽은 어떤 호출자에게도 적용되는 불변식이며,
     * CHECK 제약이 최후다. ADR-0002 가 엔티티와 CHECK 를 두 겹으로 둔 것과 같은 기준이다.
     */
    public record AdjustRequest(
            @NotNull(message = "재고 수량을 입력하세요.")
            @PositiveOrZero(message = "재고 수량은 0 이상이어야 합니다.")
            Integer quantity
    ) {
    }

    // docs/API.md 4-5 의 계약대로 camelCase 다. 메뉴 목록 응답이 snake_case 인 것과 다른 이유는
    // 그쪽이 손님 화면이 오래 써 온 계약이고 이쪽이 이번에 새로 정한 계약이기 때문이다.
    public record AdjustResponse(
            long menuId,
            int quantity
    ) {
    }
}
