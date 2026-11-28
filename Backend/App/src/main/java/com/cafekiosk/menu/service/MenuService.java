package com.cafekiosk.menu.service;


import com.cafekiosk.menu.dto.CreateMenuRequestDto;
import com.cafekiosk.menu.dto.MenuDto;
import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.repository.MenuRepository;
import com.cafekiosk.stock.entity.Stock;
import com.cafekiosk.stock.repository.StockRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {
    private final MenuRepository menuRepository;
    private final StockRepository stockRepository;

    // 조회 경로는 판매중 메뉴만 본다. 판매 중단된 메뉴는 손님 화면 목록에서도 빠지고
    // 점주의 수정 대상에서도 빠진다. 행이 남아 있다는 사실은 과거 주문만 알면 된다.
    public List<Menu> findAll(){
        return menuRepository.findAllByDeletedAtIsNullOrderByIdAsc();
    }

    /**
     * 판매중 메뉴를 남은 재고와 품절 여부까지 얹어 내려준다. FR-KSK-08.
     *
     * 조인을 쓰지 않는 이유는 docs/ERD.md 4절이다. menu 와 stock 은 재고 쪽에서만 참조하는
     * 단방향이고, 메뉴가 자기 재고를 알 필요가 없다는 판단은 재고를 함께 내려주는 이 메서드가
     * 생겨도 달라지지 않는다. 메뉴를 한 번 재고를 한 번 읽고 메모리에서 맞춘다.
     * 메뉴가 몇 개든 쿼리는 두 방이고, 재고가 메뉴를 읽는 다른 경로에 따라 올라오지도 않는다.
     *
     * 조립이 이 트랜잭션 안에 있어야 한다. stock.getMenu() 는 바로 위에서 읽어 둔 Menu 를
     * 영속성 컨텍스트에서 그대로 돌려주므로 추가 조회가 없는데, 이걸 컨트롤러로 옮기면
     * 그 보장이 OSIV 에 얹힌다. OSIV 를 끄는 날 조용히 N+1 이 되거나 lazy 초기화가 터진다.
     */
    public List<MenuDto.MenuListResponse> findAllWithStock() {
        List<Menu> menus = menuRepository.findAllByDeletedAtIsNullOrderByIdAsc();
        if (menus.isEmpty()) return List.of();

        // 키 중복은 stock.menu_id 유니크가 막는다. toMap 의 기본 동작인 예외를 그대로 둔다.
        // 유니크가 뚫린 날 재고가 둘로 갈라진 사실이 조용히 덮이는 쪽이 더 나쁘다.
        Map<Long, Stock> stocks = stockRepository
                .findAllByMenuIdIn(menus.stream().map(Menu::getId).toList())
                .stream()
                .collect(Collectors.toMap(stock -> stock.getMenu().getId(), stock -> stock));

        // 재고 행이 없는 메뉴는 get 이 null 을 돌려주고, DTO 가 그것을 수량 모름과 품절로 옮긴다
        return menus.stream()
                .map(menu -> new MenuDto.MenuListResponse(menu, stocks.get(menu.getId())))
                .toList();
    }

    /**
     * 메뉴를 고치고 고친 결과를 돌려준다. 판매 중단된 메뉴는 없는 메뉴와 같은 404 다.
     *
     * 조회와 수정과 응답 조립이 모두 이 트랜잭션 안에 있어야 한다. 예전에는 컨트롤러가 조회를 따로
     * 불러 메뉴를 받아 두었다가 이 메서드에 넘겼고, 두 호출 사이에서 트랜잭션이 한 번 끝났다.
     * open-in-view 가 없으면 그때 메뉴가 준영속이 되고, 준영속 엔티티는 고쳐도 변경 감지에 걸리지
     * 않아 UPDATE 가 나가지 않는다. 그런데도 저장됐던 것은 open-in-view 가 요청 내내 같은 영속성
     * 컨텍스트를 붙들고 있었기 때문이다. 그 설정을 끄는 날 200 을 응답하고 행은 그대로인 형태로
     * 깨진다. StockService.adjust 가 id 를 받아 조회부터 응답 조립까지 한 트랜잭션에서 끝내는 것과
     * 같은 모양으로 맞췄다.
     *
     * 누가 고치는지는 묻지 않는다. 점주 토큰이 있는지는 SecurityConfig 의 경로 표가 이미 봤고,
     * 등록자 이메일은 기록일 뿐 권한 판단에 쓰지 않는다.
     */
    @Transactional
    public MenuDto.MenuModifyResponse modify(
            long id,
            String menuName,
            int menuPrice,
            String imageUrl,
            String category
            ) {
        Menu menu = menuRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NoSuchElementException(
                        "존재하지 않는 메뉴입니다: " + id
                ));

        menu.modify(menuName, menuPrice, imageUrl, category);
        return new MenuDto.MenuModifyResponse(menu);
    }
    // 쓰기 메서드는 클래스 레벨의 readOnly = true 를 반드시 덮어써야 한다.
    // readOnly 트랜잭션에서는 Hibernate 가 flush 를 하지 않아 INSERT 도, 변경 감지가
    // 만들어 내는 UPDATE 도 예외 없이 조용히 사라진다.
    // 200 을 응답하고 행은 그대로인 형태로 깨진다.
    //
    // 삭제가 하드 삭제에서 소프트 삭제로 바뀌면서 사라지는 SQL 이 DELETE 에서
    // UPDATE 로 바뀌었을 뿐 함정의 모양은 같다. deleteMenu 의 @Transactional 을 떼면
    // deleted_at 이 채워지지 않은 채 200 이 나간다.
    //
    // 지금까지 이 두 메서드가 동작한 유일한 이유는 MenuController 의 @Transactional 이
    // read-write 트랜잭션을 먼저 열어 안쪽 readOnly 가 무시됐기 때문이다.
    // 낙관적 락 재시도는 롤백된 트랜잭션 밖 새 트랜잭션에서 일어나야 하므로(NFR-CON-05)
    // 그 컨트롤러 트랜잭션을 걷어냈고, 서비스가 자기 경계를 직접 갖는다.
    @Transactional
    public MenuDto.MenuCreateResponse createMenu(CreateMenuRequestDto req, String registrantEmail) {
        Menu menu = new Menu(req.getMenuName(),req.getImageURL(),req.getPrice(),req.getCategory(),registrantEmail);
        menuRepository.save(menu);

        // 재고 행을 같은 트랜잭션에서 함께 만든다. docs/ERD.md 3-4 가 재고 없는 메뉴를
        // 다루는 방법이 아니라 만들지 않는 방법을 규정했고, 여기가 그것을 지키는 자리다.
        // ADR-0003 이 남겨 둔 500 경로를 이 두 줄이 닫는다.
        //
        // 메뉴 저장이 먼저여야 한다. PK 가 IDENTITY 라 INSERT 가 나가야 id 가 정해지고
        // stock.menu_id 는 그 값을 참조한다. 초기 수량 0 은 Stock 이 소유한다.
        stockRepository.save(Stock.initialFor(menu));

        return new MenuDto.MenuCreateResponse(menu.getId());
    }

    /**
     * 메뉴 삭제. 행을 지우지 않고 판매를 중단한다.
     *
     * 이미 중단된 메뉴는 조회에서 걸러지므로 없는 메뉴와 같은 404 가 된다. 메뉴 수정과
     * 재고 조정도 판매 중단된 메뉴를 같은 404 로 다룬다.
     *
     * 실패할 이유가 대상이 없는 것 하나뿐인 것은 누가 불렀는지를 여기서 따지지 않기 때문이다.
     * 점주 토큰이 있는지는 SecurityConfig 의 경로 표가 이미 봤고, 등록자 이메일은 권한 판단에
     * 쓰지 않는다. 한때 이 실패가 401 이었는데, 그건 본문 이메일이 등록자와 다른 경우와 한 갈래로
     * 묶여 있었기 때문이다. 토큰을 통과한 요청에 401 을 주면 다시 로그인하라는 말이 되는데,
     * 다시 로그인해도 없는 메뉴는 여전히 없다.
     *
     * NoSuchElementException 을 고르는 이유는 GlobalExceptionHandler 가 그것을 404 로 옮기기
     * 때문이다. 중단 판단 자체는 Menu.discontinue 가 소유하고 여기서는 부르기만 한다.
     */
    @Transactional
    public void deleteMenu(long menuId) {
        Menu menu = menuRepository.findByIdAndDeletedAtIsNull(menuId)
                .orElseThrow(() -> new NoSuchElementException(
                        "존재하지 않는 메뉴입니다: " + menuId
                ));

        menu.discontinue();
    }
}
