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
            // 판매 중단된 메뉴는 없는 메뉴와 같은 400 을 받는다. 손님 화면에는
            // 판매중 메뉴만 보이므로 이 경로는 화면이 오래된 목록을 들고 있을 때만 닿는다.
            Menu menu = menuRepository.findByIdAndDeletedAtIsNull(itemRequest.menuId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "존재하지 않는 메뉴입니다: " + itemRequest.menuId()
                    ));

            // 이름과 가격 스냅샷은 OrderItem 생성자가, 총액 합산은 Order 가 책임진다
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
                    .add(toItemDTO(orderItem));
        }

        List<OrderDto.OrderSummary> summaries = new ArrayList<>();
        for (Map.Entry<Order, List<OrderDto.OrderItemDTO>> entry : orderMap.entrySet()) {
            summaries.add(toOrderSummary(entry.getKey(), entry.getValue()));
        }

        // 이메일 기준 전체 주문 묶음 반환
        return new OrderDto.OrderListResponse(
                email,
                summaries
        );
    }

    /**
     * 점주/주방용 전체 주문 목록 조회. status 가 null 이면 전체, 지정되면 해당 상태만 반환한다.
     * 주방은 먼저 들어온 주문부터 만들어야 하므로 주문 시각(orderTime) 오름차순(FIFO)으로 정렬한다.
     */
    @Transactional(readOnly = true)
    public List<OrderDto.OrderSummary> getOrdersByStatus(OrderStatus status) {
        List<Order> orders = (status == null)
                ? orderRepository.findAllByOrderByOrderTimeAsc()
                : orderRepository.findByStatusOrderByOrderTimeAsc(status);

        // N+1: order 별로 orderItems 를 lazy 순회한다. 주방 활성 주문은 소량이라
        //      지금은 허용한다. fetch join 최적화는 이후로 미룬다.
        //      이름 스냅샷이 붙은 뒤로 menu 프록시는 더 이상 초기화하지 않는다.
        List<OrderDto.OrderSummary> result = new ArrayList<>();
        for (Order order : orders) {
            List<OrderDto.OrderItemDTO> items = order.getOrderItems().stream()
                    .map(this::toItemDTO)
                    .toList();
            result.add(toOrderSummary(order, items));
        }
        return result;
    }

    // 스냅샷을 읽는 유일한 지점이다. 여기서 orderItem.getMenu() 를 거쳐 이름이나 가격을
    // 읽는 순간 과거 주문이 현재 메뉴 값으로 소급 변경되고, 두 스냅샷 회귀 테스트가 잡는다.
    // 이 메서드가 menu 를 건드리지 않는 덕분에 판매 중단된 메뉴가 섞인 주문도 그대로 조회된다.
    private OrderDto.OrderItemDTO toItemDTO(OrderItem orderItem) {
        return new OrderDto.OrderItemDTO(
                orderItem.getMenuName(),
                orderItem.getOrderPrice(),
                orderItem.getCount()
        );
    }

    private OrderDto.OrderSummary toOrderSummary(Order order, List<OrderDto.OrderItemDTO> items) {
        return new OrderDto.OrderSummary(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getOrderTime(),
                order.getTotalPrice(),
                items
        );
    }
}