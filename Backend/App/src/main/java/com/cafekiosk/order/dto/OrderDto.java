package com.cafekiosk.order.dto;

import com.cafekiosk.order.entity.Order;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class OrderDto {

    public record CreateRequest(
            @NotBlank @Email String email,
            @NotBlank String address,
            @NotNull Integer postcode,
            @NotNull List<OrderItemRequest> items
    ) {
    }

    public record OrderItemRequest(
            @NotNull Long menuId,
            @NotNull int count
    ) {
    }

    public record CreateResponse(String message) {}

    public record OrderListRequest(
            @NotNull String email
    ){ //주문 내역을 요청하는 DTO, 이메일을 포함한다

    }

    public record OrderItemDTO(
            @NotNull String menuName,
            @NotNull int menuPrice,
            @NotNull int count
    ){

    }

    // 이메일 기준 전체 주문 내역 조회 응답
    public record OrderListResponse(
            @NotNull String email,
            @NotNull List<OrderSummary> orders
    ) { // 이메일별 주문 묶음을 반환한다
    }

    // 개별 주문(주소/우편번호별) 요약 + 해당 주문의 아이템 목록
    public record OrderSummary(
            @NotNull String address,
            @NotNull int postcode,
            @NotNull List<OrderItemDTO> items
    ) {
    }
}
