package com.cafekiosk.menu.controller;

import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.repository.MenuRepository;
import com.cafekiosk.menu.service.MenuService;
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

    @BeforeEach
    void setup() {
        Menu menu1 = new Menu("망패블", "tmpImgURL", 4500, "블렌디드", "example@example.com");
        menuRepository.save(menu1);
        Menu menu2 = new Menu("카페라떼", "tmpImgURL", 5000, "커피", "example@example.com");
        menuRepository.save(menu2);
        Menu menu3 = new Menu("뉴욕치즈케이크", "tmpImgURL", 5500, "디저트", "example@example.com");
        menuRepository.save(menu3);
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

    // ── 메뉴 삭제 DELETE /api/menu/delete/{menu_id} ──
    //
    // 이 두 테스트가 여기 있는 이유는 삭제 경로가 트랜잭션 경계에 의존하고 있기 때문이다.
    // MenuService 는 클래스 레벨이 @Transactional(readOnly = true) 인데 deleteMenu 가 그걸 덮어쓰지 않는다.
    // 지금 DELETE 가 나가는 유일한 이유는 MenuController.deleteMenu 의 @Transactional 에 참여해
    // 안쪽의 readOnly 속성이 무시되기 때문이다. 컨트롤러에서 그 애너테이션을 걷어내는 순간
    // 읽기 전용 트랜잭션에서 DELETE 를 시도하게 되고 PostgreSQL 이 거부한다.
    // 걷어내기 전에 회귀 방지선부터 세운다.

    @Test
    @DisplayName("메뉴 삭제 - 등록자 이메일이 일치하면 200")
    void t03() throws Exception {
        long menuId = menuRepository.findAll().get(2).getId(); // 뉴욕치즈케이크

        ResultActions resultActions = mvc
                .perform(
                        delete("/api/menu/delete/" + menuId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "email": "example@example.com"
                                }
                                """)
                )
                .andDo(print());

        resultActions
                .andExpect(handler().handlerType(MenuController.class))
                .andExpect(handler().methodName("deleteMenu"))
                .andExpect(status().isOk())
                .andExpect(content().string("삭제되었습니다."));

        // 응답 문자열만 보면 트랜잭션이 실제로 지웠는지 알 수 없다. 행이 사라졌는지 직접 확인한다.
        assertThat(menuRepository.findById(menuId)).isEmpty();
    }

    @Test
    @DisplayName("메뉴 삭제 - 등록자 이메일이 다르면 401 이고 메뉴는 남는다")
    void t04() throws Exception {
        long menuId = menuRepository.findAll().get(2).getId(); // 뉴욕치즈케이크

        ResultActions resultActions = mvc
                .perform(
                        delete("/api/menu/delete/" + menuId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                {
                                    "email": "stranger@example.com"
                                }
                                """)
                )
                .andDo(print());

        resultActions
                .andExpect(handler().handlerType(MenuController.class))
                .andExpect(handler().methodName("deleteMenu"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("이메일이 잘못되었거나 삭제 권한이 없습니다."));

        assertThat(menuRepository.findById(menuId)).isPresent();
    }
}