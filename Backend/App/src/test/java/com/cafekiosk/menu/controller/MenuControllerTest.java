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
}