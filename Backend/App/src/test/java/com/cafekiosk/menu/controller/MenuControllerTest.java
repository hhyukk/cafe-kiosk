package com.cafekiosk.menu.controller;

import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.repository.MenuRepository;
import com.cafekiosk.menu.service.MenuService;
import com.cafekiosk.stock.entity.Stock;
import com.cafekiosk.stock.repository.StockRepository;
import com.cafekiosk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
                .andExpect(jsonPath("$.length()").value(menus.size()));

        for (int i = 0; i < menus.size(); i++) {
            Menu menu = menus.get(i);
            resultActions
                    .andExpect(jsonPath("$[%d].menu_name".formatted(i)).value(menu.getMenuName()))
                    .andExpect(jsonPath("$[%d].img_url".formatted(i)).value(menu.getImgUrl()))
                    .andExpect(jsonPath("$[%d].price".formatted(i)).value(menu.getMenuPrice()))
                    .andExpect(jsonPath("$[%d].category".formatted(i)).value(menu.getCategory()));
        }
    }

    @Test
    @DisplayName("메뉴 수정, 200-1")
    void t01() throws Exception {
        // PostgreSQL IDENTITY 시퀀스는 롤백 후 되돌아가지 않으므로 실제 저장된 ID를 조회
        long menuId = menuRepository.findAll().get(1).getId(); // 카페라떼

        ResultActions resultActions = mvc
                .perform(
                        put("/api/menu/modify/" + menuId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "menu_name": "쿨라임 피지오",
                                    "price": 3000,
                                    "img_url": "testImgUrl",
                                    "category": "피지오",
                                    "email": "example@example.com"
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
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "menu_name": "쿨라임 피지오",
                                    "price": 3000,
                                    "img_url": "testImgUrl",
                                    "category": "피지오",
                                    "email": "example@example.com"
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
        // 인덱스가 아니라 menu_id 로 거는 이유는 findAllByDeletedAtIsNull 에 ORDER BY 가 없고,
        // 트랜잭션 밖에서 도는 다른 클래스가 남긴 행이 목록에 섞일 수 있어서다.
        // id 는 이 setup 이 만든 행 하나만 가리킨다.
        ResultActions resultActions = mvc
                .perform(get("/api/menu"))
                .andDo(print());

        resultActions
                .andExpect(status().isOk())
                // 재고가 남아 있으면 수량이 그대로 실리고 품절이 아니다
                .andExpect(jsonPath("$[?(@.menu_id == %d)].stock".formatted(재고있는메뉴.getId())).value(7))
                .andExpect(jsonPath("$[?(@.menu_id == %d)].sold_out".formatted(재고있는메뉴.getId())).value(false))
                // 재고가 0 이면 품절이다. 판정은 Stock 이 하고 화면이 계산하지 않는다
                .andExpect(jsonPath("$[?(@.menu_id == %d)].stock".formatted(품절메뉴.getId())).value(0))
                .andExpect(jsonPath("$[?(@.menu_id == %d)].sold_out".formatted(품절메뉴.getId())).value(true))
                // 재고 행이 없으면 수량을 모르므로 0 이 아니라 null 이다. 없는 것과 0 개인 것은
                // 다른 사실이고, 0 으로 적으면 응답이 사실과 다른 말을 하게 된다
                .andExpect(jsonPath("$[?(@.menu_id == %d && @.stock == null)]".formatted(재고행없는메뉴.getId())).exists())
                .andExpect(jsonPath("$[?(@.menu_id == %d)].sold_out".formatted(재고행없는메뉴.getId())).value(true));
    }

    // 메뉴 생성과 삭제가 실제로 DB 에 반영되는지는 MenuWriteTransactionTest 가 지킨다.
    // 이 클래스는 @Transactional 이라 서비스의 readOnly 트랜잭션이 여기 참여만 하고
    // readOnly 속성이 무시된다. 검증하려는 트랜잭션 경계 결함이 통째로 가려지므로
    // 그 방지선은 @Transactional 없는 클래스로 옮겼다.
}