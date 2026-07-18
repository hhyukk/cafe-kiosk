package com.cafekiosk.order.controller;

import com.cafekiosk.global.rsData.RsData;
import com.cafekiosk.order.dto.OrderDto;
import com.cafekiosk.order.entity.OrderStatus;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Order API", description = "카페 주문 생성 및 고객별 주문 내역 조회를 담당하는 API입니다.")
@RestController
@RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;

    @Operation(
            summary = "주문 등록",
            description = "사용자 이메일과 선택한 메뉴 목록을 받아 새로운 주문을 생성하고, 손님이 받아갈 대기번호를 반환합니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200-1", description = "주문 완료 (대기번호 + 주문 금액 반환)",
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
                    .body(OrderDto.CreateResponse.rejected("주문 수량은 1개 이상 100개 이하만 가능합니다."));
        }

        return ResponseEntity.ok(orderService.createOrder(request));
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

    @Operation(
            summary = "주문 목록 조회 (점주/주방)",
            description = "점주·바리스타가 볼 전체 주문 목록입니다. status 를 지정하면 해당 상태만, 생략하면 전체를 "
                    + "주문 시각 오름차순(먼저 들어온 순)으로 반환합니다. 목록이 비어 있어도 200 + 빈 배열입니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200-1", description = "주문 목록 조회 성공"),
            @ApiResponse(responseCode = "400-1", description = "status 파라미터 값이 올바르지 않음")
    })
    @GetMapping("/api/orders")
    @Transactional(readOnly = true)
    public RsData<List<OrderDto.OrderSummary>> getOrders(
            @Parameter(description = "필터링할 주문 상태. 생략 시 전체 조회", example = "IN_PROGRESS")
            @RequestParam(required = false) OrderStatus status
    ) {
        return new RsData<>(
                "200-1",
                "주문 목록 조회 성공",
                orderService.getOrdersByStatus(status)
        );
    }

    @Operation(
            summary = "주문 상태 변경 (점주)",
            description = "점주가 주문 상태를 다음 단계로 전이시킵니다. (IN_PROGRESS / READY / COMPLETED / CANCELLED) "
                    + "허용되지 않은 전이는 409로 거부됩니다."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "상태 변경 성공"),
            @ApiResponse(responseCode = "409", description = "허용되지 않은 상태 전이"),
            @ApiResponse(responseCode = "400", description = "존재하지 않는 주문 또는 잘못된 요청")
    })

    @PatchMapping("/api/order/{orderId}/status")
    @Transactional
    public ResponseEntity<OrderDto.ChangeStatusResponse> changeStatus(
            @PathVariable Long orderId,
            @Valid @RequestBody OrderDto.ChangeStatusRequest request) {

        orderService.changeStatus(orderId, request.status());

        return ResponseEntity.ok(
                new OrderDto.ChangeStatusResponse("주문 상태가 변경되었습니다.")
        );
    }
}
