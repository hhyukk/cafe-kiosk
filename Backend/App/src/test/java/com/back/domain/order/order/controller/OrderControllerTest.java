package com.back.domain.order.order.controller;

import com.back.domain.order.menu.entity.Menu;
import com.back.domain.order.menu.repository.MenuRepository;
import com.back.domain.order.order.dto.OrderDto;
import com.back.domain.order.order.repository.OrderRepository;
import com.back.domain.order.order.service.OrderService;
import com.back.domain.order.customer.entity.Customer;
import com.back.domain.order.customer.repository.CustomerRepository;
import com.back.domain.order.order.entity.Order;
import com.back.domain.order.orderitem.entity.OrderItem;
import com.back.domain.order.orderitem.repository.OrderItemRepository;
import com.back.support.AbstractIntegrationTest;
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

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();

        menu1 = new Menu("아메리카노", "img1", 3000, "커피", "example@example.com");
        menu2 = new Menu("카페라떼", "img2", 4000, "커피", "example@example.com");
        menuRepository.saveAll(List.of(menu1, menu2));

        // 주문 조회 테스트를 위한 기본 주문 하나 생성
        OrderDto.CreateRequest createRequest = new OrderDto.CreateRequest(
                "order@example.com",
                "서울시 강남구 테헤란로",
                12345,
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
                  "address": "서울시 서초구 서초대로",
                  "postcode": 54321,
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
                .andExpect(jsonPath("$.message").value("주문이 성공적으로 등록되었습니다."));

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
                .andExpect(jsonPath("$.orders[0].address").value(expected.orders().get(0).address()))
                .andExpect(jsonPath("$.orders[0].postcode").value(expected.orders().get(0).postcode()))
                .andExpect(jsonPath("$.orders[0].items[0].menuName").value(expected.orders().get(0).items().get(0).menuName()))
                .andExpect(jsonPath("$.orders[0].items[0].menuPrice").value(expected.orders().get(0).items().get(0).menuPrice()))
                .andExpect(jsonPath("$.orders[0].items[0].count").value(expected.orders().get(0).items().get(0).count()));
    }

    private Long menu1Id;
    private Long menu2Id;

    @BeforeEach
    void setUp() {
        Menu menu1 = menuRepository.save(
                new Menu("아메리카노", "tmpImgURL", 4500, "음료", "example@example.com"));
        Menu menu2 = menuRepository.save(
                new Menu("카페라떼", "tmpImgURL", 5000, "음료", "example@example.com"));

        menu1Id = menu1.getId();
        menu2Id = menu2.getId();
    }

    @Test
    @DisplayName("주문 생성 성공 - 신규 고객")
    void t1() throws Exception {
        String requestBody = String.format("""
                {
                    "email": "newcustomer@test.com",
                    "address": "서울시 강남구",
                    "postcode": 12345,
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
                """, menu1Id, menu2Id);

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
        assertThat(orders.get(0).getAddress()).isEqualTo("서울시 강남구");
        assertThat(orders.get(0).getPostcode()).isEqualTo(12345);

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
                    "address": "서울시 송파구",
                    "postcode": 54321,
                    "items": [
                        {
                            "menuId": %d,
                            "count": 3
                        }
                    ]
                }
                """, menu1Id);

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
                    "address": "서울시 강남구",
                    "postcode": 12345,
                    "items": [
                        {
                            "menuId": %d,
                            "count": 2
                        }
                    ]
                }
                """, menu1Id);

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
                    "address": "서울시 강남구",
                    "postcode": 12345,
                    "items": [
                        {
                            "menuId": %d,
                            "count": 2
                        }
                    ]
                }
                """, menu1Id);

        mvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("주문 생성 실패 - 주소 누락")
    void t5() throws Exception {
        String requestBody = String.format("""
                {
                    "email": "test@test.com",
                    "postcode": 12345,
                    "items": [
                        {
                            "menuId": %d,
                            "count": 2
                        }
                    ]
                }
                """, menu1Id);

        mvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("주문 생성 실패 - 우편번호 누락")
    void t6() throws Exception {
        String requestBody = String.format("""
                {
                    "email": "test@test.com",
                    "address": "서울시 강남구",
                    "items": [
                        {
                            "menuId": %d,
                            "count": 2
                        }
                    ]
                }
                """, menu1Id);

        mvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("주문 생성 실패 - 주문 아이템 누락")
    void t7() throws Exception {
        String requestBody = """
                {
                    "email": "test@test.com",
                    "address": "서울시 강남구",
                    "postcode": 12345
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
                    "address": "서울시 강남구",
                    "postcode": 12345,
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
                    "address": "서울시 강남구",
                    "postcode": 12345,
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
                    "address": "서울시 강남구",
                    "postcode": 12345,
                    "items": [
                        {
                            "menuId": %d
                        }
                    ]
                }
                """, menu1Id);

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
                    "address": "서울시 종로구",
                    "postcode": 11111,
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
                """, menu1Id, menu2Id);

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
}
