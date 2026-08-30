package com.cafekiosk.stock.lock;

import com.cafekiosk.stock.entity.Stock;
import com.cafekiosk.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 버전 비교로 경쟁을 잡는다. 세 전략 중 두 번째다.
 *
 * 다투는 손님이 없을 것으로 보고 일단 읽는다. 그래서 낙관적이다. 잠그지 않았으므로
 * 두 손님이 같은 수량을 함께 읽을 수 있고, 그 사실은 커밋 시점에야 드러난다.
 * Stock.version 이 그 판정을 맡는다.
 *
 * ── 이 클래스가 락을 걸지 않는다는 것이 요점이다 ──────────────────────────────
 *
 * acquire 가 하는 일은 잠그지 않는 평범한 조회 하나다. 비관적 락처럼 다른 트랜잭션을
 * 멈춰 세우지 않으므로 열 손님이 전부 같은 순간에 수량 3 을 읽는다. 거기까지는
 * 락이 없던 시절과 똑같다.
 *
 * 달라지는 곳은 커밋이다. Hibernate 가 UPDATE 에 where version = ? 를 붙이므로,
 * 먼저 커밋한 한 명만 성공하고 나머지는 영향받은 행이 0 이 되어 예외를 받는다.
 * 앞사람이 깎은 사실이 사라지는 lost update 가 여기서 막힌다.
 *
 * ── 그래서 이 전략은 혼자서는 요구사항을 만족하지 못한다 ──────────────────────
 *
 * 충돌한 아홉 명이 그대로 실패하면 성공은 한 건이고 재고는 2 가 남는다. NFR-CON-01 이
 * 요구하는 것은 세 건 성공에 최종 재고 0 이다. 모자란 두 건을 채우는 것이 재시도이고,
 * 재시도는 롤백된 트랜잭션 밖에서 일어나야 하므로 여기가 아니라 OrderFacade 가 맡는다.
 * NFR-CON-05.
 *
 * 세 전략 중 유일하게 바깥짝이 있어야 성립하는 전략이다. 이 클래스만 보고
 * 낙관적 락을 다 봤다고 생각하면 절반만 본 것이다.
 *
 * ── 이 빈이 안 뜨는 경우 ─────────────────────────────────────────────────────
 *
 * cafekiosk.stock.lock-strategy 값이 optimistic 일 때만 만들어진다. 값이 없으면
 * 비관적 락이 matchIfMissing 으로 뜨므로 여기에 그 장치를 두지 않는다. 기본값이
 * 둘이면 후보가 둘이 되어 어느 쪽이 주입됐는지 실행해 봐야 아는 상태가 된다.
 */
@Component
@ConditionalOnProperty(
        name = "cafekiosk.stock.lock-strategy",
        havingValue = "optimistic"
)
@RequiredArgsConstructor
public class OptimisticStockLockStrategy implements StockLockStrategy {

    private final StockRepository stockRepository;

    /**
     * 잠그지 않고 읽는다. 비관적 락과 다른 점은 이 메서드가 아니라 커밋에 있다.
     *
     * requireByMenuId 를 그대로 쓴다. 메뉴 목록이 수량을 붙이려고 부르는 것과 같은
     * 메서드다. 잠그지 않는 조회에 읽기 경로와 쓰기 경로를 나눌 이유가 없기 때문이다.
     * 비관적 락이 requireByMenuIdForUpdate 를 따로 갖는 것은 그쪽이 행을 잠가서,
     * 손님이 화면을 여는 것만으로 다른 손님의 주문을 막는 일을 피해야 했기 때문이다.
     *
     * 행이 없으면 StockNotFoundException 이다. 락이 붙었다고 없는 행이 다른 사건이
     * 되지 않는 것처럼, 락이 없다고 해서도 마찬가지다. 판정은 리포지토리가 소유한다.
     */
    @Override
    public Stock acquire(Long menuId) {
        return stockRepository.requireByMenuId(menuId);
    }
}
