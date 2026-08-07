package com.cafekiosk.order.dto;

import com.cafekiosk.order.entity.Order;
import com.cafekiosk.order.entity.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDateTime;
import java.util.List;

public class OrderDto {

    // 점주의 주문 상태 변경 요청
    public record ChangeStatusRequest(
            @NotNull OrderStatus status
    ) {
    }

    public record ChangeStatusResponse(String message) {}

    // items 에 @Valid 를 붙여야 각 OrderItemRequest 의 제약까지 검증이 전파된다.
    // @NotNull 만 두면 리스트가 비어 있지 않은지만 보고 요소 안은 들여다보지 않는다.
    public record CreateRequest(
            @NotBlank @Email String email,
            @NotNull @Valid List<OrderItemRequest> items
    ) {
    }

    // count 는 원시 int 라 @NotNull 이 아무 일도 하지 않는다. 0 이나 음수를 막는 것은 @Positive 다.
    // 컨트롤러의 총 수량 검사는 합계 기준이라 5개와 -3개가 섞인 요청을 합계 2로 통과시킨다.
    // 그러면 order_item.count CHECK 에 걸려 400 이어야 할 요청이 500 이 된다.
    public record OrderItemRequest(
            @NotNull Long menuId,
            @Positive int count
    ) {
    }

    // orderNumber = 손님이 받아가는 대기번호
    public record CreateResponse(
            String message,
            String orderNumber,
            int totalPrice
    ) {
        /** 주문이 성립하지 않은 경우. 대기번호도 금액도 없다. */
        public static CreateResponse rejected(String message) {
            return new CreateResponse(message, null, 0);
        }
    }

    public record OrderListRequest(
            @NotNull String email
    ){ //주문 내역을 요청하는 DTO, 이메일을 포함한다

    }

    // orderPrice = 주문 시점 가격 스냅샷. 현재 메뉴 가격이 아니다.
    public record OrderItemDTO(
            @NotNull String menuName,
            @NotNull int orderPrice,
            @NotNull int count
    ){

    }

    // 이메일 기준 전체 주문 내역 조회 응답
    public record OrderListResponse(
            @NotNull String email,
            @NotNull List<OrderSummary> orders
    ) { // 이메일별 주문 묶음을 반환한다
    }

    // 개별 주문(대기번호별) 요약 + 해당 주문의 아이템 목록
    // orderId, status, orderTime 은 주방 화면이 읽을 데이터다.
    public record OrderSummary(
            @NotNull Long orderId,
            @NotNull String orderNumber,
            @NotNull OrderStatus status,
            @NotNull LocalDateTime orderTime,
            @NotNull int totalPrice,
            @NotNull List<OrderItemDTO> items
    ) {
    }
}
