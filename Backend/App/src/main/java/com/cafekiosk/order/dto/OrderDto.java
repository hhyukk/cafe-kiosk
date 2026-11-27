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

    // orderPrice = 주문 시점 가격 스냅샷. 현재 메뉴 가격이 아니다.
    public record OrderItemDTO(
            @NotNull String menuName,
            @NotNull int orderPrice,
            @NotNull int count
    ){

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

    // 손님이 대기번호로 조회할 때의 응답. 인증 없이 열린 경로라 무엇을 내보내는지를 이 타입이 고정한다.
    //
    // 주방이 읽는 OrderSummary 와 필드가 거의 같은데도 타입을 나눈다. 한 타입을 같이 쓰면 주방 쪽에
    // 필드를 더하는 순간 익명 응답에도 그 필드가 실린다. 손님에게 열린 주문 응답에 email 이 실리는
    // 일이 실제로 있었다. orderId 를 빼는 것도 같은 이유다. 그 값은 점주 API 인 상태 전이가 주문을
    // 집는 손잡이라 손님 계약에 섞지 않는다.
    public record OrderLookupResponse(
            @NotNull String orderNumber,
            @NotNull OrderStatus status,
            @NotNull LocalDateTime orderTime,
            @NotNull int totalPrice,
            @NotNull List<OrderItemDTO> items
    ) {
    }
}
