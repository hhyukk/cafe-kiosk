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
import com.cafekiosk.stock.entity.Stock;
import com.cafekiosk.stock.lock.StockLockStrategy;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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

    // 재고 행을 어떻게 확보할지는 이 서비스가 정하지 않는다. 락을 갈아 끼워도
    // 이 파일은 그대로다. 지금 주입되는 것은 비관적 락 구현이다.
    private final StockLockStrategy stockLockStrategy;

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

        // 재고에 닿는 순서를 menuId 오름차순으로 고정한다. 손님 A 가 1번과 3번을
        // 손님 B 가 3번과 1번을 담으면 두 트랜잭션이 서로가 쥔 행을 기다리게 된다.
        // 락이 붙기 전에 미리 심어 둔 정렬이고, 이제부터 실제로 그 일을 한다. NFR-CON-04.
        // 취소가 재고를 되돌리는 restoreStock 도 같은 순서를 따른다.
        List<OrderDto.OrderItemRequest> itemsInLockOrder = request.items().stream()
                .sorted(Comparator.comparing(OrderDto.OrderItemRequest::menuId))
                .toList();

        for (OrderDto.OrderItemRequest itemRequest : itemsInLockOrder) {
            // 판매 중단된 메뉴는 없는 메뉴와 같은 400 을 받는다. 손님 화면에는
            // 판매중 메뉴만 보이므로 이 경로는 화면이 오래된 목록을 들고 있을 때만 닿는다.
            Menu menu = menuRepository.findByIdAndDeletedAtIsNull(itemRequest.menuId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "존재하지 않는 메뉴입니다: " + itemRequest.menuId()
                    ));

            // 부족 판단은 Stock 이 한다. 여기서 수량을 비교하지 않는다.
            // 앞선 아이템이 이미 깎인 뒤에 부족이 드러나도 이 메서드의 트랜잭션이
            // 통째로 롤백되므로 부분 차감이 남지 않는다. 보상 코드를 따로 두지 않는 이유다.
            //
            // acquire 는 재고 행이 없으면 StockNotFoundException 을 던진다.
            // 흡수하지 않고 터뜨리는 판단은 리포지토리가 소유한다. docs/ADR/ADR-0003 참고.
            //
            // 확보한다는 말이 무슨 일인지는 전략이 정한다. 비관적 락이면 여기서
            // 다른 손님을 멈춰 세운 채로 읽는다. 이 줄이 잠그지 않고 읽던 시절에는
            // 열 손님이 전부 남은 수량 3 을 읽고 각자 2 를 계산해 통째로 덮어썼다.
            Stock stock = stockLockStrategy.acquire(itemRequest.menuId());
            stock.decrease(itemRequest.count());

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
            case CANCELLED -> {
                // 전이를 먼저 통과시킨다. 이미 취소됐거나 준비완료 이후인 주문은 여기서
                // 예외가 나므로 재고에 손대지 않는다. 같은 주문을 두 번 취소해 재고가
                // 두 번 늘어나는 경로를 따로 막을 필요가 없다. Order 의 상태머신이 이미 막는다.
                order.cancel();
                restoreStock(order);
            }
            default -> throw new IllegalArgumentException(
                    "직접 전이할 수 없는 상태입니다: " + next
            );
        }
        // 영속 상태 엔티티이므로 트랜잭션 커밋 시 변경 감지로 반영됨
    }

    /**
     * 취소된 주문이 깎았던 재고를 되돌린다.
     *
     * 차감과 같은 menuId 오름차순으로 접근한다. 취소와 주문이 같은 재고 행을 반대 순서로
     * 건드리면 서로가 쥔 행을 기다려 데드락이 된다. 락이 붙은 지금부터 실제로 그렇다.
     *
     * 복구도 차감과 같은 전략으로 행을 확보한다. 한쪽만 잠그면 취소 두 건이 같은 메뉴에
     * 겹칠 때 되돌린 수량이 서로를 덮어써 재고가 덜 돌아온다. 잠긴 읽기와 안 잠긴 읽기가
     * 한 행에 섞이면 락이 가끔만 듣는 것처럼 보이는데, 원인을 찾기 가장 나쁜 모양이다.
     *
     * getMenu().getId() 는 프록시의 식별자 접근이라 menu 행을 읽지 않는다.
     * 되돌리는 수량은 주문 시점에 굳힌 count 이므로 그 사이 메뉴가 어떻게 바뀌었든 무관하다.
     */
    private void restoreStock(Order order) {
        List<OrderItem> itemsInLockOrder = order.getOrderItems().stream()
                .sorted(Comparator.comparingLong((OrderItem item) -> item.getMenu().getId()))
                .toList();

        for (OrderItem item : itemsInLockOrder) {
            Stock stock = stockLockStrategy.acquire(item.getMenu().getId());
            stock.increase(item.getCount());
        }
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