package com.cafekiosk.order.dto;

import com.cafekiosk.order.entity.Order;
import com.cafekiosk.order.entity.OrderStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class OrderDto {

    // 점주의 주문 상태 변경 요청
    public record ChangeStatusRequest(
            @NotNull OrderStatus status
    ) {
    }

    public record ChangeStatusResponse(String message) {}

    public record CreateRequest(
            @NotBlank @Email String email,
            @NotNull List<OrderItemRequest> items
    ) {
    }

    public record OrderItemRequest(
            @NotNull Long menuId,
            @NotNull int count
    ) {
    }

    // orderNumber = 손님이 받아가는 대기번호
    public record CreateResponse(
            String message,
            String orderNumber,
            int totalPrice
    ) {
        /** 주문이 성립하지 않은 경우 — 대기번호도 금액도 없다. */
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
    // orderId/status 노출은 Phase 1에서 추가한다.
    public record OrderSummary(
            @NotNull String orderNumber,
            @NotNull int totalPrice,
            @NotNull List<OrderItemDTO> items
    ) {
    }
}
