package com.cafekiosk.order.service;

import com.cafekiosk.order.entity.Customer;
import com.cafekiosk.order.repository.CustomerRepository;
import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.repository.MenuRepository;
import com.cafekiosk.order.dto.OrderDto;
import com.cafekiosk.order.entity.Order;
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
    public void createOrder(OrderDto.CreateRequest request) {
        Customer customer = customerRepository.findByEmail(request.email())
                .orElseGet(() -> customerRepository.save(
                        new Customer(request.email())
                ));

        Order order = new Order(
                customer,
                LocalDateTime.now(),
                request.address(),
                request.postcode()
        );
        orderRepository.save(order);

        for (OrderDto.OrderItemRequest itemRequest : request.items()) {
            Menu menu = menuRepository.findById(itemRequest.menuId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "존재하지 않는 메뉴입니다: " + itemRequest.menuId()
                    ));

            OrderItem orderItem = new OrderItem(
                    order,
                    menu,
                    itemRequest.count()
            );
            orderItemRepository.save(orderItem);
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

        // 주문(주소/우편번호)별로 OrderItemDTO 리스트를 그룹핑
        Map<Order, List<OrderDto.OrderItemDTO>> orderMap = new LinkedHashMap<>();

        for (OrderItem orderItem : orderItemList) {
            Order order = orderItem.getOrder();
            orderMap
                    .computeIfAbsent(order, o -> new ArrayList<>())
                    .add(new OrderDto.OrderItemDTO(
                            orderItem.getMenu().getMenuName(),
                            orderItem.getMenu().getMenuPrice(),
                            orderItem.getCount()
                    ));
        }

        List<OrderDto.OrderSummary> summaries = new ArrayList<>();
        for (Map.Entry<Order, List<OrderDto.OrderItemDTO>> entry : orderMap.entrySet()) {
            Order order = entry.getKey();
            List<OrderDto.OrderItemDTO> items = entry.getValue();

            summaries.add(new OrderDto.OrderSummary(
                    order.getAddress(),
                    order.getPostcode(),
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