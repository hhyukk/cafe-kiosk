package com.cafekiosk.stock.service;

import com.cafekiosk.menu.entity.Menu;
import com.cafekiosk.menu.repository.MenuRepository;
import com.cafekiosk.stock.dto.StockDto;
import com.cafekiosk.stock.entity.Stock;
import com.cafekiosk.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * 점주의 재고 조정을 맡는다.
 *
 * 클래스 레벨에 @Transactional(readOnly = true) 를 두지 않는 것은 OrderService 스타일을
 * 따르는 것이다. 쓰기 메서드 하나뿐인 지금 readOnly 를 기본값으로 깔면 MenuService 가
 * 길게 경고해 둔 함정만 남고 얻는 것이 없다. 읽기 메서드가 생기면 그 메서드에 명시한다.
 */
@Service
@RequiredArgsConstructor
public class StockService {

    private final MenuRepository menuRepository;
    private final StockRepository stockRepository;

    /**
     * 재고를 요청한 수량으로 맞춘다. FR-ADM-02.
     *
     * 메뉴를 먼저 조회하는 이유는 404 판정을 재고가 아니라 메뉴가 해야 하기 때문이다.
     * 재고부터 찾으면 없는 메뉴가 404 가 아니라 500 이 된다.
     *
     * 판매 중단된 메뉴는 없는 메뉴와 같은 404 다. 재고 행은 남아 있지만 그 메뉴는 목록에도
     * 안 뜨고 주문도 안 되므로 수량을 고쳐도 아무 일이 일어나지 않는다. 조회 경로가 전부
     * findByIdAndDeletedAtIsNull 로 판매중만 보는 규칙을 여기서도 지킨다.
     *
     * NoSuchElementException 을 고르는 이유는 GlobalExceptionHandler 가 그것만 404 로
     * 옮기기 때문이다. 응답 메시지는 핸들러가 고정 문자열로 덮으므로 여기 적는 menuId 는
     * 로그와 테스트에만 남는다.
     *
     * 인증은 아직 없다. 지금은 누구나 이 메서드에 닿을 수 있고, 점주만 부를 수 있게 되는 것은
     * Spring Security 가 붙는 단계다.
     */
    @Transactional
    public StockDto.AdjustResponse adjust(long menuId, int quantity) {
        Menu menu = menuRepository.findByIdAndDeletedAtIsNull(menuId)
                .orElseThrow(() -> new NoSuchElementException(
                        "존재하지 않는 메뉴입니다: " + menuId
                ));

        // 재고 행이 없으면 만들어 주지 않고 터뜨린다. 관리 API 가 어긋난 데이터를 조용히
        // 정상으로 바꾸면 어떤 경로가 그 상태를 만들었는지 영영 드러나지 않는다. ADR-0003 참고.
        Stock stock = stockRepository.requireByMenuId(menu.getId());

        // 수량 규칙은 Stock 이 소유한다. 여기서 음수를 검사하지 않는다.
        stock.adjustTo(quantity);

        // 응답 조립을 트랜잭션 안에서 끝낸다. 컨트롤러는 트랜잭션 밖이라
        // 준영속 엔티티를 넘기면 언젠가 lazy 접근이 섞여 들어온다.
        return new StockDto.AdjustResponse(menu.getId(), stock.getQuantity());
    }
}
