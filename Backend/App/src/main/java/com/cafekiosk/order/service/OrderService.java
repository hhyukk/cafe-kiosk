package com.cafekiosk.order.service;

import com.cafekiosk.order.entity.Customer;
import com.cafekiosk.order.repository.CustomerRepository;
import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.repository.MenuRepository;
import com.cafekiosk.order.dto.OrderDto;
import com.cafekiosk.order.entity.Order;
import com.cafekiosk.order.entity.OrderStatus;
import com.cafekiosk.order.repository.OrderRepository;
import com.cafekiosk.order.entity.OrderItem;
import com.cafekiosk.order.repository.OrderItemRepository;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


@Service
@RequiredArgsConstructor
public class OrderService {

    private final CustomerRepository customerRepository;
    private final OrderRepository orderRepository;
    private final MenuRepository menuRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public OrderDto.CreateResponse createOrder(OrderDto.CreateRequest request) {
        Customer customer = customerRepository.findByEmail(request.email())
                .orElseGet(() -> customerRepository.save(
                        new Customer(request.email())
                ));

        Order order = new Order(customer, LocalDateTime.now());

        // 대기번호는 PK에서 파생되므로 채번(INSERT) 이후에야 발급할 수 있다
        orderRepository.save(order);
        order.assignOrderNumber();

        for (OrderDto.OrderItemRequest itemRequest : request.items()) {
            Menu menu = menuRepository.findById(itemRequest.menuId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "존재하지 않는 메뉴입니다: " + itemRequest.menuId()
                    ));

            // 가격 스냅샷은 OrderItem 생성자가, 총액 합산은 Order 가 책임진다
            OrderItem orderItem = new OrderItem(order, menu, itemRequest.count());
            order.addOrderItem(orderItem);
            orderItemRepository.save(orderItem);
        }

        return new OrderDto.CreateResponse(
                "주문이 성공적으로 등록되었습니다.",
                order.getOrderNumber(),
                order.getTotalPrice()
        );
    }

    /**
     * 주문 상태를 변경한다. 상태 전이 규칙 검증은 Order 엔티티의 전이 메서드가 담당하며,
     * 잘못된 전이는 InvalidOrderStatusTransitionException 으로 차단된다.
     * 상태 변경은 반드시 이 서비스를 통해서만 이루어진다(컨트롤러 setter 호출 금지).
     */
    @Transactional
    public void changeStatus(Long orderId, OrderStatus next) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 주문입니다: " + orderId
                ));

        switch (next) {
            case IN_PROGRESS -> order.startPreparing();
            case READY -> order.markReady();
            case COMPLETED -> order.complete();
            case CANCELLED -> order.cancel();
            default -> throw new IllegalArgumentException(
                    "직접 전이할 수 없는 상태입니다: " + next
            );
        }
        // 영속 상태 엔티티이므로 트랜잭션 커밋 시 변경 감지로 반영됨
    }

    @Transactional(readOnly = true)
    public OrderDto.OrderListResponse getOrderList(@NotNull String email) {
        // email을 기준으로 해당 고객의 주문 상품 목록 조회
        List<OrderItem> orderItemList = orderItemRepository.findByOrderCustomerEmail(email);

        if (orderItemList.isEmpty()) {
            // 주문 내역이 없으면 빈 리스트를 반환
            return new OrderDto.OrderListResponse(email, List.of());
        }

        // 주문(대기번호)별로 OrderItemDTO 리스트를 그룹핑
        Map<Order, List<OrderDto.OrderItemDTO>> orderMap = new LinkedHashMap<>();

        for (OrderItem orderItem : orderItemList) {
            Order order = orderItem.getOrder();
            orderMap
                    .computeIfAbsent(order, o -> new ArrayList<>())
                    .add(new OrderDto.OrderItemDTO(
                            orderItem.getMenu().getMenuName(),
                            // 현재 메뉴 가격이 아니라 주문 시점 스냅샷을 읽는다.
                            // 여기서 menu.getMenuPrice() 를 읽으면 과거 주문 금액이 소급 변경된다.
                            orderItem.getOrderPrice(),
                            orderItem.getCount()
                    ));
        }

        List<OrderDto.OrderSummary> summaries = new ArrayList<>();
        for (Map.Entry<Order, List<OrderDto.OrderItemDTO>> entry : orderMap.entrySet()) {
            Order order = entry.getKey();
            List<OrderDto.OrderItemDTO> items = entry.getValue();

            summaries.add(new OrderDto.OrderSummary(
                    order.getOrderNumber(),
                    order.getTotalPrice(),
                    items
            ));
        }

        // 이메일 기준 전체 주문 묶음 반환
        return new OrderDto.OrderListResponse(
                email,
                summaries
        );
    }
}