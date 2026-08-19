package com.cafekiosk.stock.controller;

import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.repository.MenuRepository;
import com.cafekiosk.stock.entity.Stock;
import com.cafekiosk.stock.exception.StockNotFoundException;
import com.cafekiosk.stock.repository.StockRepository;
import com.cafekiosk.stock.service.StockService;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 점주의 재고 조정이 실제로 DB 에 반영되는지를 지키는 회귀 방지선이다.
 *
 * ── 이 클래스에 @Transactional 이 없는 것이 핵심이다 ──────────────────────────────
 *
 * StockService.adjust 에서 @Transactional 을 떼면 메뉴와 재고 조회가 각자 트랜잭션에서
 * 끝나고 돌아온 엔티티가 준영속이 된다. adjustTo 는 메모리 위의 값만 바꾸고 더티 체킹이
 * 돌지 않아 UPDATE 가 나가지 않는다. 200 을 응답하고 행은 그대로인 형태로 깨진다.
 *
 * 그런데 테스트에 @Transactional 을 붙이면 그 결함이 통째로 가려진다. 테스트가 열어 둔
 * 트랜잭션에 서비스가 참여하게 되어 조정된 엔티티가 같은 영속성 컨텍스트에 남고,
 * 이어지는 재조회가 DB 가 아니라 그 인스턴스를 돌려주기 때문이다.
 * MenuWriteTransactionTest 와 OrderStockTest 가 트랜잭션 밖에 서 있는 것과 같은 이유다.
 *
 * ── 롤백이 없으므로 만든 행을 직접 지운다 ────────────────────────────────────────
 *
 * 정리를 빠뜨리면 MenuControllerTest 가 목록을 인덱스로 도는 자리와 OrderControllerTest 가
 * 길이를 세는 자리에 이 클래스가 남긴 메뉴가 섞인다. 외래키 안쪽부터 stock, menu 순으로 지운다.
 * 이 클래스는 주문을 만들지 않으므로 order_item 과 orders 는 정리 대상이 아니다.
 */
@AutoConfigureMockMvc
public class StockControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private StockService stockService;

    private final List<Long> 만든메뉴 = new ArrayList<>();

    /** 재고 3. dev 시드의 산토스와 같은 값이다. */
    private Menu 산토스;

    @BeforeEach
    void setup() {
        산토스 = 메뉴와_재고를_심는다("재고 조정 확인용 산토스", 3);
    }

    @AfterEach
    void cleanup() {
        for (Long menuId : 만든메뉴) {
            stockRepository.findByMenuId(menuId).ifPresent(stockRepository::delete);
            menuRepository.findById(menuId).ifPresent(menuRepository::delete);
        }
        만든메뉴.clear();
    }

    @Test
    @DisplayName("재고를 조정하면 200 이고 수량이 실제로 바뀐다")
    void 재고를_조정하면_200이고_수량이_실제로_바뀐다() throws Exception {
        mvc.perform(patch("/api/stock/" + 산토스.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(조정본문(50)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("재고를 조정하였습니다."))
                .andExpect(jsonPath("$.data.menuId").value(산토스.getId()))
                .andExpect(jsonPath("$.data.quantity").value(50));

        // 응답이 50 이라고 말하는 것과 행이 50 인 것은 다른 사실이다. 서비스가 트랜잭션을
        // 잃으면 앞엣것만 참이 되므로 커밋된 수량을 반드시 다시 읽는다
        assertThat(재고(산토스)).isEqualTo(50);
    }

    @Test
    @DisplayName("재고를 0 으로 조정하면 메뉴 목록이 품절로 바뀐다")
    void 재고를_0으로_조정하면_메뉴_목록이_품절로_바뀐다() throws Exception {
        // 0 은 정상 요청이다. 오늘 재료가 다 떨어졌다는 것을 점주가 적는 방법이 이것뿐이다
        mvc.perform(patch("/api/stock/" + 산토스.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(조정본문(0)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(0));

        assertThat(재고(산토스)).isZero();

        // 조정과 품절 표시가 만나는 지점이다. 여기가 깨지면 점주가 품절 처리를 해도
        // 손님 화면은 여전히 담기 버튼을 열어 둔다
        mvc.perform(get("/api/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.menu_id == %d)].stock".formatted(산토스.getId())).value(0))
                .andExpect(jsonPath("$[?(@.menu_id == %d)].sold_out".formatted(산토스.getId())).value(true));
    }

    @Test
    @DisplayName("음수로 조정하면 400 이고 수량은 그대로다")
    void 음수로_조정하면_400이고_수량은_그대로다() throws Exception {
        mvc.perform(patch("/api/stock/" + 산토스.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(조정본문(-1)))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.message").value(containsString("재고 수량은 0 이상이어야 합니다.")));

        assertThat(재고(산토스)).isEqualTo(3);
    }

    @Test
    @DisplayName("수량을 보내지 않으면 400 이다")
    void 수량을_보내지_않으면_400이다() throws Exception {
        // quantity 가 원시 int 였다면 이 요청이 0 으로 채워져 품절 처리로 조용히 성립한다.
        // 점주가 필드 이름을 잘못 적은 순간 메뉴가 품절이 되는 셈이다
        mvc.perform(patch("/api/stock/" + 산토스.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"))
                .andExpect(jsonPath("$.message").value(containsString("재고 수량을 입력하세요.")));

        assertThat(재고(산토스)).isEqualTo(3);
    }

    @Test
    @DisplayName("없는 메뉴의 재고는 조정할 수 없다")
    void 없는_메뉴는_404다() throws Exception {
        mvc.perform(patch("/api/stock/" + Long.MAX_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(조정본문(50)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"))
                .andExpect(jsonPath("$.message").value("해당 데이터가 존재하지 않습니다."));
    }

    @Test
    @DisplayName("판매가 중단된 메뉴의 재고는 조정할 수 없다")
    void 판매가_중단된_메뉴는_404다() throws Exception {
        Menu 중단할메뉴 = 메뉴와_재고를_심는다("재고 조정 확인용 중단 메뉴", 5);

        // discontinue() 를 직접 부르지 않고 HTTP 로 중단시킨다. 이 클래스에는 테스트를 감싸는
        // 트랜잭션이 없어서 엔티티 메서드를 직접 부르면 더티 체킹이 돌 자리가 없다
        mvc.perform(delete("/api/menu/delete/" + 중단할메뉴.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "email": "example@example.com"
                                }
                                """))
                .andExpect(status().isOk());

        // 재고 행은 남아 있지만 그 메뉴는 목록에도 안 뜨고 주문도 안 된다.
        // 수량을 고쳐 봐야 아무 일도 일어나지 않으므로 없는 메뉴와 같게 다룬다
        mvc.perform(patch("/api/stock/" + 중단할메뉴.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(조정본문(50)))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"));

        assertThat(재고(중단할메뉴)).isEqualTo(5);
    }

    @Test
    @DisplayName("재고 행이 없는 메뉴는 조정할 수 없다")
    void 재고_행이_없는_메뉴는_조정할_수_없다() {
        // 메뉴 등록이 재고 행을 함께 만들게 된 뒤로 정상 흐름에서는 이 상태가 나오지 않는다.
        // 리포지토리를 직접 부르는 것은 엔티티를 우회한 쓰기를 흉내 내기 위해서다.
        Menu 재고없는메뉴 = menuRepository.save(
                new Menu("재고 조정 확인용 재고 없는 메뉴", "tmpImgUrl", 5000, "디저트", "example@example.com"));
        만든메뉴.add(재고없는메뉴.getId());

        // 관리 API 가 재고 행을 만들어 주면 어긋난 데이터가 조용히 정상으로 바뀌고,
        // 어떤 경로가 그 상태를 만들었는지 영영 드러나지 않는다. 만들지 않고 터뜨린다.
        //
        // MockMvc 가 아니라 서비스를 직접 부른다. GlobalExceptionHandler 가 다루지 않는
        // 예외는 상태 코드로 바뀌지 않고 perform 자리에서 그대로 튀어나온다.
        //
        // 타입까지 단언하는 것은 예외가 리포지토리 프록시를 지나며 바뀌지 않았는지 보기 위한
        // 것이다. 프록시의 예외 변환 인터셉터가 감싸면 도메인 판단이 인프라 예외로 뭉개진다
        assertThatThrownBy(() -> stockService.adjust(재고없는메뉴.getId(), 50))
                .isInstanceOf(StockNotFoundException.class)
                .hasMessageContaining("재고 행이 없는 메뉴입니다");
    }

    private Menu 메뉴와_재고를_심는다(String menuName, int quantity) {
        Menu menu = menuRepository.save(
                new Menu(menuName, "tmpImgUrl", 12000, "커피원두", "example@example.com"));
        stockRepository.save(new Stock(menu, quantity));
        만든메뉴.add(menu.getId());
        return menu;
    }

    // 커밋된 수량을 읽는다. 이 클래스에는 테스트를 감싸는 트랜잭션이 없으므로
    // 이 조회는 자기 트랜잭션에서 돌고, 앞선 요청이 롤백했으면 롤백된 값을 본다
    private int 재고(Menu menu) {
        return stockRepository.findByMenuId(menu.getId()).orElseThrow().getQuantity();
    }

    private String 조정본문(int quantity) {
        return """
                {
                  "quantity": %d
                }
                """.formatted(quantity);
    }
}
