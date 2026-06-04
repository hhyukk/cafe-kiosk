package com.cafekiosk.order.controller;

import com.cafekiosk.order.dto.OrderDto;
import com.cafekiosk.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Order API", description = "카페 주문 생성 및 고객별 주문 내역 조회를 담당하는 API입니다.")
@RestController
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @Operation(
            summary = "주문 등록",
            description = "사용자 이메일과 선택한 메뉴 목록을 받아 새로운 주문을 생성합니다. 주문 정보는 시스템에 영구 저장됩니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200-1", description = "주문 완료 (성공 메시지 반환)",
                    content = @Content(schema = @Schema(implementation = OrderDto.CreateResponse.class))),
            @ApiResponse(responseCode = "400-1", description = "요청 데이터 유효성 검사 실패")
    })
    @PostMapping("/api/order")
    @Transactional
    public ResponseEntity<OrderDto.CreateResponse> createOrder(
            @Valid @RequestBody OrderDto.CreateRequest request) {

        // 주문 최대 수량 100개 제한 (전체 수량 기준)
        int totalCount = request.items()
                .stream()
                .mapToInt(OrderDto.OrderItemRequest::count)
                .sum();

        if (totalCount <= 0 || totalCount > 100) {
            return ResponseEntity
                    .badRequest()
                    .body(new OrderDto.CreateResponse("주문 수량은 1개 이상 100개 이하만 가능합니다."));
        }

        orderService.createOrder(request);

        return ResponseEntity.ok(
                new OrderDto.CreateResponse("주문이 성공적으로 등록되었습니다.")
        );
    }

    @PostMapping("/api/order/list")
    @Transactional(readOnly = true)
    public ResponseEntity<OrderDto.OrderListResponse> orderList(
            @Parameter(description = "조회할 고객의 이메일", example = "user@example.com")
            @Valid
            @RequestBody OrderDto.OrderListRequest request
    ) {
        OrderDto.OrderListResponse response = orderService.getOrderList(request.email());
        if (response.orders().isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(response);
    }


}
