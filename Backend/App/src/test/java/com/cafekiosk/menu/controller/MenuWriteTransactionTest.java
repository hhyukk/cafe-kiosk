package com.cafekiosk.menu.controller;

import com.cafekiosk.auth.jwt.AccessTokenIssuer;
import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.repository.MenuRepository;
import com.cafekiosk.stock.entity.Stock;
import com.cafekiosk.stock.repository.StockRepository;
import com.cafekiosk.support.AbstractIntegrationTest;
import com.cafekiosk.support.OwnerToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;

import static com.cafekiosk.support.OwnerToken.점주_토큰;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.handler;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 메뉴 쓰기 경로가 실제로 DB 에 반영되는지를 지키는 회귀 방지선이다.
 *
 * ── 이 클래스에 @Transactional 이 없는 것이 핵심이다 ──────────────────────────────
 *
 * MenuService 는 클래스 레벨이 @Transactional(readOnly = true) 다. 쓰기 메서드가 그걸
 * 덮어쓰지 않으면 Hibernate 가 flush 를 하지 않아 INSERT 와 UPDATE 가 예외 없이 사라진다.
 * 삭제도 행을 지우지 않고 판매를 중단하는 일이라 UPDATE 다.
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

    @Autowired
    private StockRepository stockRepository;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    private final List<Long> 만든메뉴 = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (Long menuId : 만든메뉴) {
            // 외래키 안쪽부터 지운다. 메뉴 등록이 재고 행을 함께 만들게 되면
            // 이 순서를 지키지 않는 순간 stock.menu_id 에 걸려 메뉴 삭제가 실패한다.
            // 리포지토리로 직접 심은 메뉴는 재고 행이 없어 ifPresent 가 그냥 넘어간다.
            stockRepository.findByMenuId(menuId).ifPresent(stockRepository::delete);
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
        ResultActions resultActions = mvc.perform(
                        post("/api/menu")
                                .with(점주_토큰(accessTokenIssuer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
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
                .andExpect(jsonPath("$.resultCode").value("201-1"))
                .andExpect(jsonPath("$.message").value("생성 완료되었습니다."));

        // 응답은 트랜잭션이 커밋됐는지 알려주지 않는다. 행을 직접 찾는다.
        List<Menu> 저장된메뉴 = menuRepository.findAll().stream()
                .filter(menu -> menu.getMenuName().equals("트랜잭션 경계 확인용 케이크"))
                .toList();

        assertThat(저장된메뉴).hasSize(1);
        assertThat(저장된메뉴.get(0).getMenuPrice()).isEqualTo(6500);
        만든메뉴.add(저장된메뉴.get(0).getId());

        // 응답이 돌려준 id 는 방금 커밋된 그 행을 가리켜야 한다. 등록한 화면은 이 값으로 곧바로
        // 재고를 조정하므로, 다른 행을 가리키면 엉뚱한 메뉴의 재고가 바뀐다
        resultActions.andExpect(jsonPath("$.data.menu_id").value(저장된메뉴.get(0).getId()));
    }

    @Test
    @DisplayName("메뉴 생성 - 재고 행이 같은 트랜잭션에서 함께 생기고 수량은 0 이다")
    void 메뉴생성이_재고행도_함께_만든다() throws Exception {
        mvc.perform(
                        post("/api/menu")
                                .with(점주_토큰(accessTokenIssuer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "category": "디저트",
                                            "menuName": "재고 행 확인용 케이크",
                                            "price": 6500,
                                            "imageURL": "tmpImgURL"
                                        }
                                        """)
                )
                .andDo(print())
                .andExpect(status().isCreated());

        Menu 저장된메뉴 = menuRepository.findAll().stream()
                .filter(menu -> menu.getMenuName().equals("재고 행 확인용 케이크"))
                .findFirst()
                .orElseThrow();
        만든메뉴.add(저장된메뉴.getId());

        // 201 응답은 재고 행까지 커밋됐는지 알려주지 않는다. 행을 직접 찾는다.
        // 여기가 비면 재고 없는 메뉴가 다시 생기기 시작한 것이고, 그 메뉴는 주문하는 순간
        // OrderStockTest 가 고정한 그 StockNotFoundException 을 맞는다.
        Stock 재고 = stockRepository.findByMenuId(저장된메뉴.getId()).orElseThrow();
        assertThat(재고.getQuantity()).isZero();

        // 이 수량이 0 이라는 사실과 재고 0 이 목록에서 품절로 내려간다는 사실이 만나
        // 새 메뉴는 품절로 태어난다가 성립한다. 뒤엣것은 MenuControllerTest 가 지킨다.
    }

    @Test
    @DisplayName("메뉴 생성 - 등록자는 요청 본문이 아니라 토큰의 이메일로 기록된다")
    void 등록자는_토큰의_이메일로_기록된다() throws Exception {
        mvc.perform(
                        post("/api/menu")
                                .with(점주_토큰(accessTokenIssuer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "category": "디저트",
                                            "menuName": "등록자 확인용 케이크",
                                            "price": 6500,
                                            "imageURL": "tmpImgURL"
                                        }
                                        """)
                )
                .andDo(print())
                .andExpect(status().isCreated());

        Menu 저장된메뉴 = menuRepository.findAll().stream()
                .filter(menu -> menu.getMenuName().equals("등록자 확인용 케이크"))
                .findFirst()
                .orElseThrow();
        만든메뉴.add(저장된메뉴.getId());

        // 본문에는 이메일이 없다. 그런데도 등록자가 채워졌다면 그 값이 올 수 있는 곳은 토큰뿐이다.
        // 이 클래스는 트랜잭션 밖에 서 있으므로 위에서 읽은 Menu 는 요청을 처리한
        // 영속성 컨텍스트가 아니라 DB 에서 왔다. 커밋된 값을 보는 것이다.
        assertThat(저장된메뉴.getEmail()).isEqualTo(OwnerToken.테스트_점주_이메일);
    }

    @Test
    @DisplayName("메뉴 수정 - 200 이고 바뀐 값이 실제로 남는다")
    void 메뉴수정이_DB에_반영된다() throws Exception {
        Menu menu = 메뉴를_심는다("수정 전 케이크", OwnerToken.테스트_점주_이메일);

        mvc.perform(
                        put("/api/menu/modify/" + menu.getId())
                                .with(점주_토큰(accessTokenIssuer))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                            "menu_name": "수정 후 케이크",
                                            "price": 7000,
                                            "img_url": "changedImgURL",
                                            "category": "케이크"
                                        }
                                        """)
                )
                .andDo(print())
                .andExpect(handler().handlerType(MenuController.class))
                .andExpect(handler().methodName("modifyMenu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menu_name").value("수정 후 케이크"));

        // 응답은 서비스가 메모리 위의 엔티티로 만든 것이라 UPDATE 가 커밋됐는지 알려 주지 않는다.
        // 수정이 readOnly 트랜잭션에 갇히면 응답은 바뀐 값을 말하는데 행은 그대로다. 행을 다시 읽는다.
        //
        // 이 테스트가 지키지 못하는 것도 적어 둔다. MockMvc 요청에도 open-in-view 가 켜져 있어서,
        // 컨트롤러가 조회해 둔 엔티티를 서비스에 넘기던 예전 구조에서도 이 테스트는 초록이었다.
        // 그 구조를 잡으려면 open-in-view 를 끈 컨텍스트가 하나 더 떠야 하는데, application-test.yml 이
        // 적어 두었듯 컨텍스트마다 커넥션 풀이 따로 열려 PostgreSQL 연결 한도에 다가간다.
        // open-in-view 에 기대지 않는다는 것은 MenuService.modify 가 조회부터 응답 조립까지
        // 한 트랜잭션에서 끝내는 구조가 보장한다.
        Menu 커밋된메뉴 = menuRepository.findById(menu.getId()).orElseThrow();
        assertThat(커밋된메뉴.getMenuName()).isEqualTo("수정 후 케이크");
        assertThat(커밋된메뉴.getMenuPrice()).isEqualTo(7000);
        assertThat(커밋된메뉴.getImgUrl()).isEqualTo("changedImgURL");
        assertThat(커밋된메뉴.getCategory()).isEqualTo("케이크");
    }

    @Test
    @DisplayName("메뉴 삭제 - 200 이고 행은 남되 판매가 중단된다")
    void 메뉴삭제가_DB에_반영된다() throws Exception {
        // 등록자를 토큰의 이메일과 같게 심어 이 테스트에서 등록자라는 변수를 없앤다.
        // 여기서는 판매 중단이 DB 에 남는지만 보고, 등록자가 달라도 지워지는지는 아래 테스트가 본다.
        Menu menu = 메뉴를_심는다("판매 중단 확인용 케이크", OwnerToken.테스트_점주_이메일);

        mvc.perform(
                        delete("/api/menu/delete/" + menu.getId())
                                .with(점주_토큰(accessTokenIssuer))
                )
                .andDo(print())
                .andExpect(handler().handlerType(MenuController.class))
                .andExpect(handler().methodName("deleteMenu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("삭제되었습니다."));

        // 소프트 삭제는 두 얼굴을 함께 만족해야 한다. 하나만 보면 절반만 검증된다.
        //
        // 첫째, 행은 남아 있고 deleted_at 이 채워졌다. 이 단언이 readOnly 트랜잭션에
        // 갇혀 UPDATE 가 사라지는 결함을 잡는다. 하드 삭제 시절 DELETE 가 사라지던 것과
        // 같은 함정이고, 그래서 이 클래스는 여전히 @Transactional 없이 서 있다.
        Menu 커밋된메뉴 = menuRepository.findById(menu.getId()).orElseThrow();
        assertThat(커밋된메뉴.isDiscontinued()).isTrue();

        // 둘째, 판매중 조회에서는 사라졌다. 리포지토리와 HTTP 두 층에서 함께 본다.
        // 이름이 아니라 menu_id 로 거르는 이유는 이 클래스가 롤백 없이 도는 탓에
        // 다른 테스트가 남긴 행이 목록에 섞일 수 있어서다. id 는 이 테스트가 만든 행 하나만 가리킨다.
        assertThat(menuRepository.findByIdAndDeletedAtIsNull(menu.getId())).isEmpty();

        // 필터는 목록이 아닌 곳에 걸려도 예외 없이 빈 결과를 내고 isEmpty 는 그것을 통과시킨다.
        // data 가 목록이라는 것을 먼저 세워야 빠졌다는 단언이 무언가를 검사한다
        mvc.perform(get("/api/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.menu_id == %d)]".formatted(menu.getId())).isEmpty());
    }

    @Test
    @DisplayName("메뉴 삭제 - 등록자가 아니어도 점주 토큰이 있으면 200 이고 판매가 중단된다")
    void 등록자가_아니어도_점주_토큰이면_판매가_중단된다() throws Exception {
        Menu menu = 메뉴를_심는다("남이 등록한 뉴욕치즈케이크", "example@example.com");

        // 전제부터 고정한다. 등록자와 토큰의 이메일이 같으면 이 테스트는 위 테스트를
        // 한 번 더 도는 것에 그친다.
        assertThat(menu.getEmail()).isNotEqualTo(OwnerToken.테스트_점주_이메일);

        // 예전에는 이 조합이 컨트롤러의 본문 이메일 비교에 걸려 401 이었고, 이 테스트도 그 401 을
        // 지키고 있었다. 요구사항이 뒤집혀 등록자 이메일은 기록일 뿐 권한 판단에 쓰지 않으므로
        // 같은 조합이 이제 200 이어야 한다. 등록자 비교가 되살아나는 순간 여기가 깨진다.
        mvc.perform(
                        delete("/api/menu/delete/" + menu.getId())
                                .with(점주_토큰(accessTokenIssuer))
                )
                .andDo(print())
                .andExpect(handler().handlerType(MenuController.class))
                .andExpect(handler().methodName("deleteMenu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCode").value("200-1"))
                .andExpect(jsonPath("$.message").value("삭제되었습니다."));

        // 응답만 봐서는 판매 중단이 커밋됐는지 모른다. 행을 다시 읽어 확인한다.
        Menu 커밋된메뉴 = menuRepository.findById(menu.getId()).orElseThrow();
        assertThat(커밋된메뉴.isDiscontinued()).isTrue();
    }
}
