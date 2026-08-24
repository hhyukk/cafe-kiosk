package com.cafekiosk.stock.lock;

import com.cafekiosk.stock.entity.Stock;
import com.cafekiosk.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * DB 행 락으로 경쟁을 막는다. 세 전략 중 첫 번째이자 기본값이다.
 *
 * 다투는 손님이 있을 것을 미리 가정하고 먼저 잠근 다음 읽는다. 그래서 비관적이다.
 * 뒤에 온 트랜잭션은 앞 트랜잭션이 커밋하거나 롤백할 때까지 SELECT 자리에서 멈추므로,
 * 깨어났을 때 읽는 값은 이미 앞사람이 깎고 간 값이다. 덮어쓸 여지가 사라진다.
 *
 * ── 트랜잭션 밖에서 부르면 안 된다 ────────────────────────────────────────────
 *
 * 행 락은 트랜잭션이 끝나야 풀린다. 트랜잭션 없이 부르면 풀 자리가 없으므로
 * Hibernate 가 거부한다. 지금 호출자는 OrderService 의 createOrder 와 changeStatus
 * 둘뿐이고 둘 다 @Transactional 이다. 새 호출자를 만들 때 이 조건을 먼저 본다.
 *
 * ── 이 빈이 안 뜨는 경우 ─────────────────────────────────────────────────────
 *
 * cafekiosk.stock.lock-strategy 값이 pessimistic 이 아니면 이 빈은 만들어지지 않는다.
 * 값이 아예 없으면 뜬다. 낙관적 락과 분산 락이 붙기 전까지는 다른 후보가 없으므로,
 * 오타로 엉뚱한 값을 적으면 StockLockStrategy 빈을 못 찾는다는 오류로 컨텍스트가 죽는다.
 * 그 오류를 보면 락이 아니라 이 프로퍼티부터 본다.
 *
 * 전략마다 조건을 자기 파일에 들고 있게 하는 것은 다음 전략이 붙을 때 이 파일을
 * 고치지 않게 하려는 것이다. 한곳에 모은 팩토리를 두면 전략을 더할 때마다 그 파일이 커진다.
 */
@Component
@ConditionalOnProperty(
        name = "cafekiosk.stock.lock-strategy",
        havingValue = "pessimistic",
        matchIfMissing = true
)
@RequiredArgsConstructor
public class PessimisticStockLockStrategy implements StockLockStrategy {

    private final StockRepository stockRepository;

    /**
     * SELECT ... FOR UPDATE 로 행을 잠근 채 가져온다.
     *
     * 잠그는 범위는 menuId 하나에 대응하는 재고 행 한 줄이다. 테이블 전체가 아니므로
     * 다른 메뉴를 주문하는 손님은 여기서 기다리지 않는다.
     *
     * 여러 메뉴를 담은 주문이 이 메서드를 여러 번 부르는데, 부르는 순서는 호출자가
     * menuId 오름차순으로 고정한다. 손님 A 가 1번과 3번을, 손님 B 가 3번과 1번을 담았을 때
     * 서로가 쥔 행을 기다리는 상황을 그 정렬이 막는다. NFR-CON-04.
     */
    @Override
    public Stock acquire(Long menuId) {
        return stockRepository.requireByMenuIdForUpdate(menuId);
    }
}
