package com.cafekiosk.menu.controller;

import com.cafekiosk.auth.jwt.AccessTokenIssuer;
import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.repository.MenuRepository;
import com.cafekiosk.menu.service.MenuService;
import com.cafekiosk.stock.entity.Stock;
import com.cafekiosk.stock.repository.StockRepository;
import com.cafekiosk.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static com.cafekiosk.support.OwnerToken.점주_토큰;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@Transactional
public class MenuControllerTest extends AbstractIntegrationTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private MenuService menuService;

    @Autowired
    private MenuRepository menuRepository;

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    private Menu 재고있는메뉴;
    private Menu 품절메뉴;
    private Menu 재고행없는메뉴;

    @BeforeEach
    void setup() {
        Menu menu1 = new Menu("망패블", "tmpImgURL", 4500, "블렌디드", "example@example.com");
        menuRepository.save(menu1);
        stockRepository.save(new Stock(menu1, 7));

        Menu menu2 = new Menu("카페라떼", "tmpImgURL", 5000, "커피", "example@example.com");
        menuRepository.save(menu2);
        stockRepository.save(new Stock(menu2, 0));

        // menu3 은 재고 행을 일부러 심지 않는다. 메뉴 등록이 재고를 함께 만들게 된 뒤로
        // 정상 흐름에서는 나올 수 없는 상태지만, 엔티티를 우회한 쓰기로는 여전히 만들어진다.
        // 그때 목록이 무엇을 내려주는지가 계약이므로 여기서 고정한다.
        // OrderStockTest 의 재고 행 없는 메뉴 테스트가 리포지토리를 직접 부르는 것과 같은 이유다.
        Menu menu3 = new Menu("뉴욕치즈케이크", "tmpImgURL", 5500, "디저트", "example@example.com");
        menuRepository.save(menu3);

        재고있는메뉴 = menu1;
        품절메뉴 = menu2;
        재고행없는메뉴 = menu3;
    }

    @Test
    @DisplayName("메뉴 조회")
    void t00() throws Exception {
        ResultActions resultActions = mvc
                .perform(get("/api/menu"))
                .andDo(print());

        List<Menu> menus = menuService.findAll();

        resultActions
                .andExpect(handler().handlerType(MenuController.class))
                .andExpect(handler().methodName("getMenus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                // 봉투가 아니라 data 의 길이를 센다. 봉투도 필드가 셋이라 setup 이 심은 메뉴 셋과
                // 숫자가 같아서, $.length() 로 두면 경로가 틀려도 통과한다
                .andExpect(jsonPath("$.data.length()").value(menus.size()));

        for (int i = 0; i < menus.size(); i++) {
            Menu menu = menus.get(i);
            resultActions
                    .andExpect(jsonPath("$.data[%d].menu_name".formatted(i)).value(menu.getMenuName()))
                    .andExpect(jsonPath("$.data[%d].img_url".formatted(i)).value(menu.getImgUrl()))
                    .andExpect(jsonPath("$.data[%d].price".formatted(i)).value(menu.getMenuPrice()))
                    .andExpect(jsonPath("$.data[%d].category".formatted(i)).value(menu.getCategory()));
        }
    }

    @Test
    @DisplayName("메뉴 수정, 200-1")
    void t01() throws Exception {
        // PostgreSQL IDENTITY 시퀀스는 롤백 후 되돌아가지 않으므로 실제 저장된 ID를 조회
        long menuId = menuRepository.findAll().get(1).getId(); // 카페라떼

        // setup 이 심은 메뉴의 등록자는 example@example.com 이고 토큰의 이메일은 owner@test.com 이라
        // 서로 다르다. 그래도 수정된다. 등록자 이메일로 권한을 가르지 않는다는 것을 지키는 자리는
        // MenuWriteTransactionTest 이고 여기서는 덤이다.
        ResultActions resultActions = mvc
                .perform(
                        put("/api/menu/modify/" + menuId)
                        .with(점주_토큰(accessTokenIssuer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "menu_name": "쿨라임 피지오",
                                    "price": 3000,
                                    "img_url": "testImgUrl",
                                    "category": "피지오"
                                }
                                """)
                )
                .andDo(print());

        resultActions
                .andExpect(handler().handlerType(MenuController.class))
                .andExpect(handler().methodName("modifyMenu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("메뉴를 수정하였습니다."))
                .andExpect(jsonPath("$.data.menu_id").value(menuId))
                .andExpect(jsonPath("$.data.menu_name").value("쿨라임 피지오"))
                .andExpect(jsonPath("$.data.price").value(3000))
                .andExpect(jsonPath("$.data.category").value("피지오"));
    }

    @Test
    @DisplayName("메뉴 수정, 404-1")
    void t02() throws Exception {
        int menuId = Integer.MAX_VALUE;

        ResultActions resultActions = mvc
                .perform(
                        put("/api/menu/modify/" + menuId)
                                .with(점주_토큰(accessTokenIssuer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "menu_name": "쿨라임 피지오",
                                    "price": 3000,
                                    "img_url": "testImgUrl",
                                    "category": "피지오"
                                }
                                """)
                )
                .andDo(print());

        resultActions
                .andExpect(handler().handlerType(MenuController.class))
                .andExpect(handler().methodName("modifyMenu"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"))
                .andExpect(jsonPath("$.message").value("해당 데이터가 존재하지 않습니다."));

    }

    @Test
    @DisplayName("메뉴 조회 - 남은 재고와 품절 여부가 함께 내려온다")
    void t03() throws Exception {
        // 인덱스가 아니라 menu_id 로 거는 이유는 트랜잭션 밖에서 도는 다른 클래스가 남긴 행이
        // 목록에 섞일 수 있어서다. 목록이 menu_id 순으로 정렬돼 있어도 이 setup 의 행이 몇 번째에
        // 올지는 그 행들에 달려 있다. id 는 이 setup 이 만든 행 하나만 가리킨다.
        ResultActions resultActions = mvc
                .perform(get("/api/menu"))
                .andDo(print());

        resultActions
                .andExpect(status().isOk())
                // 재고가 남아 있으면 수량이 그대로 실리고 품절이 아니다
                .andExpect(jsonPath("$.data[?(@.menu_id == %d)].stock".formatted(재고있는메뉴.getId())).value(7))
                .andExpect(jsonPath("$.data[?(@.menu_id == %d)].sold_out".formatted(재고있는메뉴.getId())).value(false))
                // 재고가 0 이면 품절이다. 판정은 Stock 이 하고 화면이 계산하지 않는다
                .andExpect(jsonPath("$.data[?(@.menu_id == %d)].stock".formatted(품절메뉴.getId())).value(0))
                .andExpect(jsonPath("$.data[?(@.menu_id == %d)].sold_out".formatted(품절메뉴.getId())).value(true))
                // 재고 행이 없으면 수량을 모르므로 0 이 아니라 null 이다. 없는 것과 0 개인 것은
                // 다른 사실이고, 0 으로 적으면 응답이 사실과 다른 말을 하게 된다
                .andExpect(jsonPath("$.data[?(@.menu_id == %d && @.stock == null)]".formatted(재고행없는메뉴.getId())).exists())
                .andExpect(jsonPath("$.data[?(@.menu_id == %d)].sold_out".formatted(재고행없는메뉴.getId())).value(true));
    }

    @Test
    @DisplayName("메뉴 삭제, 없는 메뉴는 404-1")
    void t04() throws Exception {
        int menuId = Integer.MAX_VALUE;

        ResultActions resultActions = mvc
                .perform(
                        delete("/api/menu/delete/" + menuId)
                                .with(점주_토큰(accessTokenIssuer))
                )
                .andDo(print());

        resultActions
                .andExpect(handler().handlerType(MenuController.class))
                .andExpect(handler().methodName("deleteMenu"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"))
                .andExpect(jsonPath("$.message").value("해당 데이터가 존재하지 않습니다."));
    }

    @Test
    @DisplayName("메뉴 삭제, 이미 판매 중단된 메뉴도 404-1")
    void t05() throws Exception {
        // 판매 중단된 메뉴는 조회에서 걸러져 없는 메뉴와 같은 답을 받는다. 예전에는 이 경우가
        // 본문 이메일이 등록자와 다를 때와 한 갈래로 묶여 401 이었다. 토큰을 통과한 요청에
        // 401 을 주면 다시 로그인하라는 말이 되는데, 다시 로그인해도 판매 중단된 메뉴는 그대로다.
        Menu 판매중단메뉴 = new Menu("단종된 시즌 라떼", "tmpImgURL", 6000, "커피", "example@example.com");
        판매중단메뉴.discontinue();
        menuRepository.save(판매중단메뉴);

        ResultActions resultActions = mvc
                .perform(
                        delete("/api/menu/delete/" + 판매중단메뉴.getId())
                                .with(점주_토큰(accessTokenIssuer))
                )
                .andDo(print());

        resultActions
                .andExpect(handler().handlerType(MenuController.class))
                .andExpect(handler().methodName("deleteMenu"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.resultCode").value("404-1"));
    }

    @Test
    @DisplayName("메뉴 조회, 메뉴를 수정해도 목록은 menu_id 오름차순이다")
    void t06() throws Exception {
        // PostgreSQL 은 UPDATE 를 하면 그 행의 새 버전을 테이블 뒤쪽에 쓴다. 조회에 정렬 기준이
        // 없으면 저장된 순서대로 돌려주므로 점주가 고친 메뉴가 목록 끝으로 밀리고, 손님 화면은
        // 그 순서를 그대로 그린다. setup 이 가장 먼저 심은 메뉴를 고쳐 그 상황을 만든다.
        mvc.perform(
                        put("/api/menu/modify/" + 재고있는메뉴.getId())
                                .with(점주_토큰(accessTokenIssuer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "menu_name": "이름을 바꾼 망패블",
                                            "price": 4800,
                                            "img_url": "tmpImgURL",
                                            "category": "블렌디드"
                                        }
                                        """)
                )
                .andExpect(status().isOk());

        // 고친 내용은 아직 이 테스트의 트랜잭션 안에만 있다. 아래 조회가 쿼리를 내기 직전에
        // Hibernate 가 밀려 있던 UPDATE 를 먼저 내보내므로 조회는 새 버전을 본다.
        String body = mvc.perform(get("/api/menu"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        // 특정 메뉴가 몇 번째인지가 아니라 전체가 오름차순인지를 본다. 트랜잭션 밖에서 도는
        // 다른 클래스가 남긴 행이 섞일 수 있어 자리는 이 테스트가 정할 수 없다.
        List<Long> 메뉴아이디 = JsonPath.<List<Number>>read(body, "$.data[*].menu_id").stream()
                .map(Number::longValue)
                .toList();

        // 와일드카드 경로는 엉뚱한 곳을 가리켜도 예외 없이 빈 리스트를 돌려주고, 빈 리스트는 언제나
        // 정렬돼 있다. 봉투를 씌운 뒤에도 옛 경로 $[*] 를 두면 이 단언이 그렇게 통과한다
        assertThat(메뉴아이디).isNotEmpty().isSorted();
    }

    // t07 부터 t09 는 메뉴 수정이, t10 은 메뉴 등록이 가격 규칙을 지키는지 본다. 가격은 0원 이상 10,000,000원 이하다. FR-MNU-03.
    // 규칙의 경계값 자체는 MenuTest 가 POJO 로 보고, 여기서는 그 판정이 HTTP 응답으로 어떻게 나가는지를 본다.

    @Test
    @DisplayName("메뉴 수정, 가격 0원은 200-1")
    void t07() throws Exception {
        // 등록은 0원을 받는데 수정만 @Positive 로 0원을 거절하던 때가 있었다
        ResultActions resultActions = mvc
                .perform(
                        put("/api/menu/modify/" + 재고있는메뉴.getId())
                                .with(점주_토큰(accessTokenIssuer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "menu_name": "망패블",
                                            "price": 0,
                                            "img_url": "tmpImgURL",
                                            "category": "블렌디드"
                                        }
                                        """)
                )
                .andDo(print());

        resultActions
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.data.price").value(0));
    }

    @Test
    @DisplayName("메뉴 수정, 가격이 10,000,000원을 넘으면 400-1")
    void t08() throws Exception {
        // 상한 검사가 등록 컨트롤러에만 있어서 수정은 얼마든 받던 때가 있었다
        ResultActions resultActions = mvc
                .perform(
                        put("/api/menu/modify/" + 재고있는메뉴.getId())
                                .with(점주_토큰(accessTokenIssuer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "menu_name": "망패블",
                                            "price": 10000001,
                                            "img_url": "tmpImgURL",
                                            "category": "블렌디드"
                                        }
                                        """)
                )
                .andDo(print());

        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"));
    }

    @Test
    @DisplayName("메뉴 수정, 가격이 빠지면 400-1")
    void t09() throws Exception {
        // 가격 필드가 int 라 본문에서 빠지면 0 이 된다. 0 은 정상 가격이므로 막지 않으면
        // 가격을 빠뜨린 요청이 메뉴를 조용히 0원으로 만든다. @Positive 가 그 0 을 거절하면서
        // 이 규칙을 우연히 함께 지키고 있었으므로, 그 어노테이션을 걷어낼 때 잃지 않게 여기서 붙잡는다
        ResultActions resultActions = mvc
                .perform(
                        put("/api/menu/modify/" + 재고있는메뉴.getId())
                                .with(점주_토큰(accessTokenIssuer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "menu_name": "망패블",
                                            "img_url": "tmpImgURL",
                                            "category": "블렌디드"
                                        }
                                        """)
                )
                .andDo(print());

        resultActions
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"));
    }

    @Test
    @DisplayName("메뉴 등록, 가격이 10,000,000원을 넘으면 400-1")
    void t10() throws Exception {
        // 등록 컨트롤러가 같은 범위를 앞단에서 먼저 보고 평문으로 400 을 내던 때가 있었다.
        // 그 검사를 걷어낸 뒤로는 수정과 같은 Menu 의 판정을 지나 봉투로 나간다
        ResultActions resultActions = mvc
                .perform(
                        post("/api/menu")
                                .with(점주_토큰(accessTokenIssuer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "category": "블렌디드",
                                            "menuName": "너무 비싼 망패블",
                                            "price": 10000001,
                                            "imageURL": "tmpImgURL"
                                        }
                                        """)
                )
                .andDo(print());

        resultActions
                .andExpect(handler().handlerType(MenuController.class))
                .andExpect(handler().methodName("createMenu"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.resultCode").value("400-1"));
    }

    // 메뉴 생성과 삭제가 실제로 DB 에 반영되는지는 MenuWriteTransactionTest 가 지킨다.
    // 이 클래스는 @Transactional 이라 서비스의 readOnly 트랜잭션이 여기 참여만 하고
    // readOnly 속성이 무시된다. 검증하려는 트랜잭션 경계 결함이 통째로 가려지므로
    // 그 방지선은 @Transactional 없는 클래스로 옮겼다.
}