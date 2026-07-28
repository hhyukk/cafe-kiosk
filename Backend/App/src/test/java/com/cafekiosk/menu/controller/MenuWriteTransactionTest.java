package com.cafekiosk.menu.controller;

import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.repository.MenuRepository;
import com.cafekiosk.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 메뉴 쓰기 경로가 실제로 DB 에 반영되는지를 지키는 회귀 방지선이다.
 *
 * ── 이 클래스에 @Transactional 이 없는 것이 핵심이다 ──────────────────────────────
 *
 * MenuService 는 클래스 레벨이 @Transactional(readOnly = true) 다. 쓰기 메서드가 그걸
 * 덮어쓰지 않으면 Hibernate 가 flush 를 하지 않아 INSERT 와 DELETE 가 예외 없이 사라진다.
 * 200 을 응답하고 행은 그대로 남는 형태로 깨지므로 응답만 봐서는 알 수 없다.
 *
 * 그런데 테스트 클래스에 @Transactional 을 붙이면 이 결함이 통째로 가려진다.
 * 테스트가 read-write 트랜잭션을 먼저 열어 두면 서비스의 readOnly 트랜잭션은
 * propagation REQUIRED 로 거기 참여만 하고 readOnly 속성이 무시되기 때문이다.
 * 실제로 이 검증은 한때 @Transactional 이 붙은 MenuControllerTest 안에 있었는데,
 * 서비스에서 @Transactional 을 떼도 초록으로 남아 아무것도 잡지 못했다.
 * 그래서 트랜잭션 밖으로 옮겨 왔다.
 *
 * 롤백이 없으므로 만든 행은 @AfterEach 에서 직접 지운다.
 * deleteAll 을 쓰지 않는 이유는 다른 테스트가 남긴 것이나 order_item 외래키에
 * 걸릴 여지를 만들 필요가 없어서다. 이 테스트가 만든 id 만 지운다.
 */
@AutoConfigureMockMvc
public class MenuWriteTransactionTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MenuRepository menuRepository;

    private final List<Long> 만든메뉴 = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long menuId : 만든메뉴) {
            menuRepository.findById(menuId).ifPresent(menuRepository::delete);
        }
        만든메뉴.clear();
    }

    private Menu 메뉴를_심는다(String menuName, String email) {
        Menu menu = menuRepository.save(new Menu(menuName, "tmpImgURL", 5500, "디저트", email));
        만든메뉴.add(menu.getId());
        return menu;
    }

    @Test
    @DisplayName("메뉴 생성 - 201 이고 행이 실제로 남는다")
    void 메뉴생성이_DB에_반영된다() throws Exception {
        // BFF 가 보내는 필드명 그대로다. frontend/src/app/api/menu/route.ts 의 POST 핸들러 참고
        mvc.perform(
                        post("/api/menu")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "email": "example@example.com",
                                            "category": "디저트",
                                            "menuName": "트랜잭션 경계 확인용 케이크",
                                            "price": 6500,
                                            "imageURL": "tmpImgURL"
                                        }
                                        """)
                )
                .andDo(print())
                .andExpect(handler().handlerType(MenuController.class))
                .andExpect(handler().methodName("createMenu"))
                .andExpect(status().isCreated())
                .andExpect(content().string("생성 완료되었습니다."));

        // 응답 문자열은 트랜잭션이 커밋됐는지 알려주지 않는다. 행을 직접 찾는다.
        List<Menu> 저장된메뉴 = menuRepository.findAll().stream()
                .filter(menu -> menu.getMenuName().equals("트랜잭션 경계 확인용 케이크"))
                .toList();

        assertThat(저장된메뉴).hasSize(1);
        assertThat(저장된메뉴.get(0).getMenuPrice()).isEqualTo(6500);
        만든메뉴.add(저장된메뉴.get(0).getId());
    }

    @Test
    @DisplayName("메뉴 삭제 - 등록자 이메일이 일치하면 200 이고 행이 사라진다")
    void 메뉴삭제가_DB에_반영된다() throws Exception {
        Menu menu = 메뉴를_심는다("뉴욕치즈케이크", "example@example.com");

        mvc.perform(
                        delete("/api/menu/delete/" + menu.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "email": "example@example.com"
                                        }
                                        """)
                )
                .andDo(print())
                .andExpect(handler().handlerType(MenuController.class))
                .andExpect(handler().methodName("deleteMenu"))
                .andExpect(status().isOk())
                .andExpect(content().string("삭제되었습니다."));

        assertThat(menuRepository.findById(menu.getId())).isEmpty();
    }

    @Test
    @DisplayName("메뉴 삭제 - 등록자 이메일이 다르면 401 이고 메뉴는 남는다")
    void 등록자가_아니면_메뉴가_지워지지_않는다() throws Exception {
        Menu menu = 메뉴를_심는다("남의 뉴욕치즈케이크", "example@example.com");

        mvc.perform(
                        delete("/api/menu/delete/" + menu.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "email": "stranger@example.com"
                                        }
                                        """)
                )
                .andDo(print())
                .andExpect(handler().handlerType(MenuController.class))
                .andExpect(handler().methodName("deleteMenu"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("이메일이 잘못되었거나 삭제 권한이 없습니다."));

        assertThat(menuRepository.findById(menu.getId())).isPresent();
    }
}
