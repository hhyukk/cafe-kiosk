package com.cafekiosk.order.controller;

import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.repository.MenuRepository;
import com.cafekiosk.order.dto.OrderDto;
import com.cafekiosk.order.repository.OrderRepository;
import com.cafekiosk.order.service.OrderService;
import com.cafekiosk.order.entity.Customer;
import com.cafekiosk.order.repository.CustomerRepository;
import com.cafekiosk.order.entity.Order;
import com.cafekiosk.order.entity.OrderItem;
import com.cafekiosk.order.repository.OrderItemRepository;
import com.cafekiosk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Transactional
public class OrderControllerTest extends AbstractIntegrationTest {

    private MockMvc mvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private OrderService orderService;

    private Menu menu1;
    private Menu menu2;

    // 예전엔 @BeforeEach 가 setup() / setUp() 두 개였다(이름만 대소문자 차이).
    // JUnit 5는 둘 다 실행하고 순서를 보장하지 않아 매 테스트마다 메뉴가 중복 생성됐다 — 하나로 합쳤다.
    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();

        menu1 = new Menu("아메리카노", "img1", 3000, "커피", "example@example.com");
        menu2 = new Menu("카페라떼", "img2", 4000, "커피", "example@example.com");
        menuRepository.saveAll(List.of(menu1, menu2));

        // 주문 조회 테스트를 위한 기본 주문 하나 생성
        OrderDto.CreateRequest createRequest = new OrderDto.CreateRequest(
                "order@example.com",
                List.of(
                        new OrderDto.OrderItemRequest(menu1.getId(), 2),
                        new OrderDto.OrderItemRequest(menu2.getId(), 1)
                )
        );
        orderService.createOrder(createRequest);
    }

    @Test
    @DisplayName("주문 생성 - POST /api/order")
    void t00_createOrder() throws Exception {
        // given
        String email = "test-create@example.com";

        String json = """
                {
                  "email": "%s",
                  "items": [
                    { "menuId": %d, "count": 1 },
                    { "menuId": %d, "count": 3 }
                  ]
                }
                """.formatted(email, menu1.getId(), menu2.getId());

        // when
        ResultActions resultActions = mvc
                .perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json)
                )
                .andDo(print());

        // then
        resultActions
                .andExpect(handler().handlerType(OrderController.class))
                .andExpect(handler().methodName("createOrder"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("주문이 성공적으로 등록되었습니다."))
                // 손님은 대기번호를 받아간다
                .andExpect(jsonPath("$.orderNumber").isNotEmpty())
                // 아메리카노(3000) x1 + 카페라떼(4000) x3
                .andExpect(jsonPath("$.totalPrice").value(15000));

        // DB에 주문/주문상품이 잘 저장되었는지 간단 검증
        assertThat(orderRepository.findByCustomerEmail(email)).hasSize(1);
        assertThat(orderItemRepository.findByOrderCustomerEmail(email)).hasSize(2);
    }

    @Test
    @DisplayName("주문 내역 조회 - POST /api/order/list")
    void t01_getOrderList() throws Exception {
        // given
        String email = "order@example.com";
        String json = """
                {
                  "email": "%s"
                }
                """.formatted(email);

        OrderDto.OrderListResponse expected = orderService.getOrderList(email);

        // when
        ResultActions resultActions = mvc
                .perform(
                        post("/api/order/list")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)
                )
                .andDo(print());

        // then — OrderListResponse > orders[0](OrderSummary) > items[0](OrderItemDTO) 구조
        resultActions
                .andExpect(handler().handlerType(OrderController.class))
                .andExpect(handler().methodName("orderList"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(expected.email()))
                .andExpect(jsonPath("$.orders.length()").value(expected.orders().size()))
                .andExpect(jsonPath("$.orders[0].orderNumber").value(expected.orders().get(0).orderNumber()))
                .andExpect(jsonPath("$.orders[0].totalPrice").value(expected.orders().get(0).totalPrice()))
                .andExpect(jsonPath("$.orders[0].items[0].menuName").value(expected.orders().get(0).items().get(0).menuName()))
                .andExpect(jsonPath("$.orders[0].items[0].orderPrice").value(expected.orders().get(0).items().get(0).orderPrice()))
                .andExpect(jsonPath("$.orders[0].items[0].count").value(expected.orders().get(0).items().get(0).count()));
    }

    @Test
    @DisplayName("주문 생성 성공 - 신규 고객")
    void t1() throws Exception {
        String requestBody = String.format("""
                {
                    "email": "newcustomer@test.com",
                    "items": [
                        {
                            "menuId": %d,
                            "count": 2
                        },
                        {
                            "menuId": %d,
                            "count": 1
                        }
                    ]
                }
                """, menu1.getId(), menu2.getId());

        mvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("주문이 성공적으로 등록되었습니다."));

        Customer customer = customerRepository.findByEmail("newcustomer@test.com").orElse(null);
        assertThat(customer).isNotNull();
        assertThat(customer.getEmail()).isEqualTo("newcustomer@test.com");

        // findAll()은 setup()이 만든 주문까지 포함 → 이메일로 좁혀서 검증
        List<Order> orders = orderRepository.findByCustomerEmail("newcustomer@test.com");
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getOrderNumber()).isNotBlank();
        // 아메리카노(3000) x2 + 카페라떼(4000) x1
        assertThat(orders.get(0).getTotalPrice()).isEqualTo(10000);

        List<OrderItem> orderItems = orderItemRepository.findByOrderCustomerEmail("newcustomer@test.com");
        assertThat(orderItems).hasSize(2);
    }

    @Test
    @DisplayName("주문 생성 성공 - 기존 고객")
    void t2() throws Exception {
        Customer existingCustomer = customerRepository.save(
                new Customer("existing@test.com"));

        String requestBody = String.format("""
                {
                    "email": "existing@test.com",
                    "items": [
                        {
                            "menuId": %d,
                            "count": 3
                        }
                    ]
                }
                """, menu1.getId());

        mvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("주문이 성공적으로 등록되었습니다."));

        List<Customer> customers = customerRepository.findAll();
        long customerCount = customers.stream()
                .filter(c -> c.getEmail().equals("existing@test.com"))
                .count();
        assertThat(customerCount).isEqualTo(1);

        // findAll()은 setup()이 만든 주문까지 포함 → 이메일로 좁혀서 검증
        List<Order> orders = orderRepository.findByCustomerEmail("existing@test.com");
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getCustomer().getId()).isEqualTo(existingCustomer.getId());
    }

    @Test
    @DisplayName("주문 생성 실패 - 이메일 누락")
    void t3() throws Exception {
        String requestBody = String.format("""
                {
                    "items": [
                        {
                            "menuId": %d,
                            "count": 2
                        }
                    ]
                }
                """, menu1.getId());

        mvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("주문 생성 실패 - 유효하지 않은 이메일 형식")
    void t4() throws Exception {
        String requestBody = String.format("""
                {
                    "email": "invalidemail",
                    "items": [
                        {
                            "menuId": %d,
                            "count": 2
                        }
                    ]
                }
                """, menu1.getId());

        mvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("가격 스냅샷 - 메뉴 가격을 바꿔도 과거 주문 금액은 변하지 않는다")
    void 가격스냅샷_메뉴가격변경이_과거주문에_소급되지_않는다() {
        // given — setup()이 심은 주문: 아메리카노(3000) x2 + 카페라떼(4000) x1 = 10,000원
        String email = "order@example.com";

        OrderDto.OrderListResponse 주문직후 = orderService.getOrderList(email);
        assertThat(주문직후.orders().get(0).totalPrice()).isEqualTo(10000);
        assertThat(주문직후.orders().get(0).items())
                .extracting(OrderDto.OrderItemDTO::orderPrice)
                .containsExactly(3000, 4000);

        // when — 점주가 메뉴 가격을 대폭 인상한다
        menu1.modify("아메리카노", 9000, "img1", "커피");
        menu2.modify("카페라떼", 9000, "img2", "커피");
        menuRepository.saveAll(List.of(menu1, menu2));

        // then — 이미 지나간 주문의 금액은 그대로여야 한다.
        // 스냅샷이 없다면 여기서 27,000원(9000x2 + 9000x1)이 나온다.
        OrderDto.OrderListResponse 가격인상후 = orderService.getOrderList(email);
        assertThat(가격인상후.orders().get(0).totalPrice()).isEqualTo(10000);
        assertThat(가격인상후.orders().get(0).items())
                .extracting(OrderDto.OrderItemDTO::orderPrice)
                .containsExactly(3000, 4000);
    }

    @Test
    @DisplayName("주문 생성 실패 - 주문 아이템 누락")
    void t7() throws Exception {
        String requestBody = """
                {
                    "email": "test@test.com"
                }
                """;

        mvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("주문 생성 실패 - 존재하지 않는 메뉴")
    void t8() throws Exception {
        String requestBody = """
                {
                    "email": "test@test.com",
                    "items": [
                        {
                            "menuId": 999999,
                            "count": 2
                        }
                    ]
                }
                """;

        mvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 메뉴입니다: 999999"));
    }

    @Test
    @DisplayName("주문 생성 실패 - 메뉴 ID 누락")
    void t9() throws Exception {
        String requestBody = """
                {
                    "email": "test@test.com",
                    "items": [
                        {
                            "count": 2
                        }
                    ]
                }
                """;

        mvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("주문 생성 실패 - 수량 누락")
    void t10() throws Exception {
        String requestBody = String.format("""
                {
                    "email": "test@test.com",
                    "items": [
                        {
                            "menuId": %d
                        }
                    ]
                }
                """, menu1.getId());

        mvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("주문 생성 성공 - 여러 개의 다른 메뉴 주문")
    void createOrder_MultipleMenus_Success() throws Exception {
        String requestBody = String.format("""
                {
                    "email": "multi@test.com",
                    "items": [
                        {
                            "menuId": %d,
                            "count": 1
                        },
                        {
                            "menuId": %d,
                            "count": 2
                        }
                    ]
                }
                """, menu1.getId(), menu2.getId());

        mvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("주문이 성공적으로 등록되었습니다."));

        // findAll()은 setup()이 만든 주문 아이템까지 포함 → 이메일로 좁혀서 검증
        List<OrderItem> orderItems = orderItemRepository.findByOrderCustomerEmail("multi@test.com");
        assertThat(orderItems).hasSize(2);
        assertThat(orderItems).extracting("count")
                .containsExactlyInAnyOrder(1, 2);
    }

    // setup()이 생성한 주문(order@example.com, 초기 상태 CONFIRMED)의 id를 가져온다
    private Long seededOrderId() {
        return orderRepository.findByCustomerEmail("order@example.com").get(0).getId();
    }

    @Test
    @DisplayName("주문 상태 변경 성공 - CONFIRMED → IN_PROGRESS")
    void changeStatus_success() throws Exception {
        Long orderId = seededOrderId();

        mvc.perform(patch("/api/order/{orderId}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"IN_PROGRESS\"}"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("주문 상태가 변경되었습니다."));

        assertThat(orderRepository.findById(orderId).orElseThrow().getStatus())
                .isEqualTo(com.cafekiosk.order.entity.OrderStatus.IN_PROGRESS);
    }

    @Test
    @DisplayName("주문 상태 변경 실패 - 허용되지 않은 전이는 409 (CONFIRMED → COMPLETED)")
    void changeStatus_invalidTransition_conflict() throws Exception {
        Long orderId = seededOrderId();

        mvc.perform(patch("/api/order/{orderId}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"COMPLETED\"}"))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-1"));
    }

    @Test
    @DisplayName("주문 상태 변경 실패 - 존재하지 않는 주문은 400")
    void changeStatus_orderNotFound_badRequest() throws Exception {
        mvc.perform(patch("/api/order/{orderId}/status", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"IN_PROGRESS\"}"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("존재하지 않는 주문입니다: 999999"));
    }
}
