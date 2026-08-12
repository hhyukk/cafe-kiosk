package com.cafekiosk.order.controller;

import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.repository.MenuRepository;
import com.cafekiosk.order.dto.OrderDto;
import com.cafekiosk.order.repository.CustomerRepository;
import com.cafekiosk.order.repository.OrderItemRepository;
import com.cafekiosk.order.repository.OrderRepository;
import com.cafekiosk.order.service.OrderService;
import com.cafekiosk.stock.entity.Stock;
import com.cafekiosk.stock.repository.StockRepository;
import com.cafekiosk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 주문이 재고를 깎고 취소가 되돌리는 것을 지키는 회귀 방지선이다.
 *
 * ── 이 클래스에 @Transactional 이 없는 것이 핵심이다 ──────────────────────────────
 *
 * 이 파일이 증명하려는 것은 재고 부족 주문이 부분 차감을 남기지 않는다는 사실이고,
 * 그건 값이 아니라 트랜잭션 경계에 대한 주장이다.
 *
 * 테스트에 @Transactional 을 붙이면 이 주장을 검증할 수 없게 된다. 메뉴 둘 중 앞의 하나가
 * 깎이고 뒤에서 재고가 터지는 시나리오에서, 테스트가 read-write 트랜잭션을 먼저 열어 두면
 * 깎인 Stock 이 같은 영속성 컨텍스트에 그대로 남는다. 이어지는 재조회가 DB 가 아니라 그
 * 인스턴스를 돌려주므로, 실제로는 정상 롤백되는 코드인데 테스트만 빨개진다.
 * em.clear() 로도 해결되지 않는다. 더티 체킹이 만든 UPDATE 가 같은 트랜잭션 안에서 이미
 * flush 됐기 때문에, 컨텍스트를 비우고 다시 읽어도 깎인 값이 보인다.
 *
 * MenuWriteTransactionTest 가 트랜잭션 밖에 서 있는 것과 같은 이유다.
 *
 * ── 롤백이 없으므로 만든 행을 직접 지운다 ────────────────────────────────────────
 *
 * 정리를 빠뜨리면 OrderControllerTest 가 전체 주문 목록 길이를 1 이라고 단언하는 자리에서
 * 깨진다. 두 클래스가 같은 @AutoConfigureMockMvc 조합이라 Spring 컨텍스트를 공유하고,
 * 따라서 같은 DB 를 본다. 여기서 커밋한 주문이 그대로 그 목록에 섞인다.
 *
 * 캐스케이드에 기대지 않고 외래키 안쪽부터 순서대로 지운다. order_item, orders,
 * customer, stock, menu 순이다. detached 엔티티와 초기화되지 않은 컬렉션 위에서
 * 캐스케이드가 어떻게 도는지는 한눈에 보이지 않고, 정리 코드가 그 위에 서 있으면
 * 실패했을 때 테스트가 틀렸는지 정리가 틀렸는지 구분하기 어렵다.
 */
@AutoConfigureMockMvc
public class OrderStockTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderService orderService;

    // 이 클래스가 쓰는 이메일 전부. 성공했든 롤백됐든 정리 대상에 넣는다.
    private static final List<String> 사용하는이메일 = List.of(
            "stock-over@example.com",
            "stock-exact@example.com",
            "stock-partial@example.com",
            "stock-cancel@example.com",
            "stock-missing@example.com"
    );

    private final List<Long> 만든메뉴 = new ArrayList<>();

    /** 재고 10. 부족하지 않은 쪽이다. 산토스보다 먼저 심어 menuId 가 작아야 한다. */
    private Menu 예가체프;

    /** 재고 3. 마지막 한 잔을 재현할 때 쓰는 수량이고 dev 시드와 같은 값이다. */
    private Menu 산토스;

    @BeforeEach
    void setup() {
        예가체프 = 메뉴와_재고를_심는다("재고 확인용 예가체프", 15000, 10);
        산토스 = 메뉴와_재고를_심는다("재고 확인용 산토스", 12000, 3);
    }

    @AfterEach
    void cleanup() {
        for (String email : 사용하는이메일) {
            // deleteAllInBatch 는 캐스케이드를 타지 않고 DELETE 한 방을 날린다.
            // 안쪽부터 순서대로 지우고 있으므로 캐스케이드가 할 일이 애초에 없다.
            orderItemRepository.deleteAllInBatch(orderItemRepository.findByOrderCustomerEmail(email));
            orderRepository.deleteAllInBatch(orderRepository.findByCustomerEmail(email));
            customerRepository.findByEmail(email).ifPresent(customerRepository::delete);
        }
        for (Long menuId : 만든메뉴) {
            stockRepository.findByMenuId(menuId).ifPresent(stockRepository::delete);
            menuRepository.findById(menuId).ifPresent(menuRepository::delete);
        }
        만든메뉴.clear();
    }

    @Test
    @DisplayName("재고보다 많이 주문하면 409 이고 재고는 한 개도 줄지 않는다")
    void 재고보다_많이_주문하면_409이고_재고는_그대로다() throws Exception {
        // 재고 3 에 4 잔이다. AC-04 가 요구하는 바로 그 상황이다
        mvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(주문본문("stock-over@example.com", 산토스.getId(), 4)))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-1"));

        assertThat(재고(산토스)).isEqualTo(3);
        // 재고가 부족하면 주문 자체가 성립하지 않는다. 남은 만큼만 파는 방식이 아니다
        assertThat(orderRepository.findByCustomerEmail("stock-over@example.com")).isEmpty();
    }

    @Test
    @DisplayName("남은 재고와 같은 수량은 주문할 수 있고 재고가 0 이 된다")
    void 재고와_같은_수량은_주문할_수_있다() throws Exception {
        // 경계값을 양쪽에서 잡는다. 하나 넘으면 409 라는 위 테스트와 짝이다.
        // 이 테스트가 없으면 무조건 409 를 내는 구현도 위 테스트만으로는 green 이 된다
        mvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(주문본문("stock-exact@example.com", 산토스.getId(), 3)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNumber").isNotEmpty());

        assertThat(재고(산토스)).isZero();
    }

    @Test
    @DisplayName("여러 메뉴 중 하나만 부족해도 전체가 실패하고 부분 차감이 남지 않는다")
    void 하나만_부족해도_전체가_실패하고_부분차감이_남지_않는다() throws Exception {
        // 산토스를 먼저 적어 보낸다. 서비스가 menuId 오름차순으로 정렬하므로 실제 차감은
        // 예가체프부터 일어나고 산토스에서 터진다. 요청 순서 그대로 돌면 산토스에서 바로
        // 터져 예가체프가 깎일 기회조차 없고, 그러면 이 테스트가 아무것도 증명하지 못한다
        String json = """
                {
                  "email": "stock-partial@example.com",
                  "items": [
                    { "menuId": %d, "count": 4 },
                    { "menuId": %d, "count": 1 }
                  ]
                }
                """.formatted(산토스.getId(), 예가체프.getId());

        mvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andDo(print())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.resultCode").value("409-1"));

        // 먼저 깎였던 쪽이 되돌아와 있어야 한다. 이 단언 하나가 이 클래스의 존재 이유다
        assertThat(재고(예가체프)).isEqualTo(10);
        assertThat(재고(산토스)).isEqualTo(3);

        // 주문과 아이템도 남지 않는다. 재고와 주문이 함께 all-or-nothing 이다
        assertThat(orderRepository.findByCustomerEmail("stock-partial@example.com")).isEmpty();
        assertThat(orderItemRepository.findByOrderCustomerEmail("stock-partial@example.com")).isEmpty();
    }

    @Test
    @DisplayName("주문을 취소하면 깎였던 재고가 돌아오고, 다시 취소해도 두 번 늘지 않는다")
    void 주문을_취소하면_재고가_돌아온다() throws Exception {
        String email = "stock-cancel@example.com";

        mvc.perform(post("/api/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(주문본문(email, 산토스.getId(), 2)))
                .andDo(print())
                .andExpect(status().isOk());

        assertThat(재고(산토스)).isEqualTo(1);

        long orderId = orderRepository.findByCustomerEmail(email).get(0).getId();

        mvc.perform(patch("/api/order/{orderId}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CANCELLED\"}"))
                .andDo(print())
                .andExpect(status().isOk());

        assertThat(재고(산토스)).isEqualTo(3);

        // 이미 취소된 주문은 Order 의 상태머신이 막는다. 서비스가 재고 복구 앞에
        // 별도 방어를 두지 않아도 재고가 두 번 늘지 않는 이유가 이것이다
        mvc.perform(patch("/api/order/{orderId}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\": \"CANCELLED\"}"))
                .andDo(print())
                .andExpect(status().isConflict());

        assertThat(재고(산토스)).isEqualTo(3);
    }

    @Test
    @DisplayName("재고 행이 없는 메뉴는 주문할 수 없다")
    void 재고행이_없는_메뉴는_주문할_수_없다() {
        // 재고 없는 메뉴는 팔 수 없는 메뉴다. docs/ERD.md 3-4 는 이 상태를 다루는 방법이
        // 아니라 만들지 않는 방법을 규정했으므로, 여기서 400 이나 409 로 흡수하지 않는다.
        // 메뉴 등록이 재고 행을 함께 만들게 되면 이 경로 자체가 사라진다
        Menu 재고없는메뉴 = menuRepository.save(
                new Menu("재고 행 없는 케이크", "tmpImgUrl", 5000, "디저트", "example@example.com"));
        만든메뉴.add(재고없는메뉴.getId());

        OrderDto.CreateRequest request = new OrderDto.CreateRequest(
                "stock-missing@example.com",
                List.of(new OrderDto.OrderItemRequest(재고없는메뉴.getId(), 1))
        );

        // MockMvc 가 아니라 서비스를 직접 부른다. GlobalExceptionHandler 가 다루지 않는
        // 예외는 상태 코드로 바뀌지 않고 perform 자리에서 그대로 튀어나온다
        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("재고 행이 없는 메뉴입니다");
    }

    private Menu 메뉴와_재고를_심는다(String menuName, int price, int quantity) {
        Menu menu = menuRepository.save(
                new Menu(menuName, "tmpImgUrl", price, "커피원두", "example@example.com"));
        stockRepository.save(new Stock(menu, quantity));
        만든메뉴.add(menu.getId());
        return menu;
    }

    // 커밋된 수량을 읽는다. 이 클래스에는 테스트를 감싸는 트랜잭션이 없으므로
    // 이 조회는 자기 트랜잭션에서 돌고, 앞선 요청이 롤백했으면 롤백된 값을 본다
    private int 재고(Menu menu) {
        return stockRepository.findByMenuId(menu.getId()).orElseThrow().getQuantity();
    }

    private String 주문본문(String email, long menuId, int count) {
        return """
                {
                  "email": "%s",
                  "items": [
                    { "menuId": %d, "count": %d }
                  ]
                }
                """.formatted(email, menuId, count);
    }
}
