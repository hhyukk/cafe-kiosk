package com.cafekiosk.menu.service;


import com.cafekiosk.menu.dto.CreateMenuRequestDto;
import com.cafekiosk.menu.dto.DeleteMenuRequestDto;
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
import java.util.Optional;
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
        return menuRepository.findAllByDeletedAtIsNull();
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
        List<Menu> menus = menuRepository.findAllByDeletedAtIsNull();
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

    public Optional<Menu> findById(Long id){
        return menuRepository.findByIdAndDeletedAtIsNull(id);
    }

    //이메일 유효성 검사 성공시 true 아니면 false
    @Transactional
    public boolean modify(
            Menu menu,
            String menuName,
            int menuPrice,
            String imageUrl,
            String category,
            String email
            ) {
        if (menu.getEmail().equals(email)) {
            menu.modify(menuName, menuPrice, imageUrl, category);
            return true;
        }
        else return false;
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
    public void createMenu(CreateMenuRequestDto req) {
        Menu menu = new Menu(req.getMenuName(),req.getImageURL(),req.getPrice(),req.getCategory(),req.getEmail());
        menuRepository.save(menu);

        // 재고 행을 같은 트랜잭션에서 함께 만든다. docs/ERD.md 3-4 가 재고 없는 메뉴를
        // 다루는 방법이 아니라 만들지 않는 방법을 규정했고, 여기가 그것을 지키는 자리다.
        // ADR-0003 이 남겨 둔 500 경로를 이 두 줄이 닫는다.
        //
        // 메뉴 저장이 먼저여야 한다. PK 가 IDENTITY 라 INSERT 가 나가야 id 가 정해지고
        // stock.menu_id 는 그 값을 참조한다. 초기 수량 0 은 Stock 이 소유한다.
        stockRepository.save(Stock.initialFor(menu));
    }

    /**
     * 메뉴 삭제. 성공 시 true, 아니면 false 를 돌려준다.
     *
     * 행을 지우지 않고 판매를 중단한다. 이미 중단된 메뉴는 조회에서 걸러지므로
     * 없는 메뉴와 같은 false 가 되고, 컨트롤러가 401 로 바꾼다.
     * 중단 판단 자체는 Menu.discontinue 가 소유하고 여기서는 부르기만 한다.
     */
    @Transactional
    public boolean deleteMenu(DeleteMenuRequestDto req) {
        if (req.getMenuId() == null || req.getEmail() == null) return false;
        return menuRepository.findByIdAndDeletedAtIsNull(req.getMenuId())
                .filter(menu -> menu.getEmail().equals(req.getEmail()))
                .map(menu -> {
                    menu.discontinue();
                    return true;
                })
                .orElse(false);
    }
}
