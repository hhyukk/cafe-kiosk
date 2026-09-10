package com.cafekiosk.stock.lock;

import com.cafekiosk.stock.entity.Stock;
import com.cafekiosk.stock.exception.StockLockTimeoutException;
import com.cafekiosk.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.TimeUnit;

/**
 * Redis 에서 열쇠를 받아 온 다음 읽는다. 세 전략 중 마지막이다.
 *
 * 앞의 둘은 경쟁을 DB 안에서 처리했다. 비관적 락은 행을 잠갔고 낙관적 락은 버전을 비교했다.
 * 둘 다 그 DB 를 보는 인스턴스끼리만 성립한다. 여기서는 판정을 DB 밖으로 꺼내
 * 애플리케이션이 몇 대로 늘어나도 같은 열쇠 하나를 두고 다투게 만든다.
 * 지금 이 프로젝트는 한 대로 돌지만, 재는 것은 그 한 대 안에서도 똑같이 성립한다.
 *
 * ── 락을 트랜잭션 안에서 잡고 트랜잭션 밖에서 푼다 ────────────────────────────
 *
 * 이 클래스에서 가장 중요한 대목이다. StockLockStrategy 주석이 분산 락은 트랜잭션을
 * 감싼다고 적어 두었는데, acquire 는 트랜잭션 안쪽 seam 이라 그 말과 어긋나 보인다.
 *
 * 어긋나지 않는다. 잡는 자리는 안쪽이고 푸는 자리를 커밋 이후로 미루면 락 구간이
 * 결과적으로 트랜잭션을 감싼다. 그 미루는 장치가 registerSynchronization 이다.
 *
 * 안에서 잡고 안에서 풀면 어떻게 되는지가 이 설계의 이유다. unlock 과 커밋 사이에 창이
 * 생기고, 그 창에 들어온 다음 손님이 아직 커밋되지 않은 차감 이전 값을 읽는다.
 * 열 손님이 전부 3 을 읽고 각자 2 를 쓰는, 락이 없던 시절과 똑같은 lost update 다.
 * 락은 걸려 있는데 재고만 어긋나므로 원인을 찾기 가장 나쁜 모양이 된다.
 *
 * 바깥에서 감싸는 다른 방법도 있었다. OrderFacade 가 트랜잭션 밖에 서 있으니 거기서
 * 락을 잡으면 순서가 자연스럽다. 그런데 파사드는 어느 전략이 주입됐는지 모르는 채로
 * 서 있어야 하고, 전략별 분기가 생기는 순간 세 전략을 비교할 때 파사드가 상수가
 * 아니게 된다. 수치 차이를 락 때문이라고 말할 수 없게 되는 것이 그 대가다.
 * 그리고 파사드는 menuId 를 모른다. 요청 DTO 를 열어 봐야 아는데, 그러면 파사드가
 * 주문의 내용을 읽기 시작한다.
 *
 * ── afterCommit 이 아니라 afterCompletion 이다 ───────────────────────────────
 *
 * 롤백일 때도 락은 풀려야 한다. 재고가 부족해 OutOfStockException 이 나가는 경로가
 * 매번 롤백인데, afterCommit 을 쓰면 그 열쇠가 watchdog 만료까지 남는다. 재고 3 에
 * 열 손님이면 일곱 번 그렇게 되고, 그다음 손님은 30 초를 기다린 뒤에야 주문한다.
 *
 * afterCompletion 이 트랜잭션을 수행한 그 스레드에서 불린다는 점도 조건이다.
 * RLock 은 획득한 스레드만 해제할 수 있고 다른 스레드가 부르면
 * IllegalMonitorStateException 이다. 이 훅은 그 조건을 저절로 만족한다.
 *
 * ── 이 빈이 안 뜨는 경우 ─────────────────────────────────────────────────────
 *
 * cafekiosk.stock.lock-strategy 값이 distributed 일 때만 만들어진다.
 * 낙관적 락과 같은 이유로 matchIfMissing 을 두지 않는다. 기본값이 둘이면 어느 쪽이
 * 주입됐는지 실행해 봐야 아는 상태가 된다.
 */
@Component
@ConditionalOnProperty(
        name = "cafekiosk.stock.lock-strategy",
        havingValue = "distributed"
)
@RequiredArgsConstructor
public class DistributedStockLockStrategy implements StockLockStrategy {

    /**
     * 재고 행 하나에 열쇠 하나다. menuId 를 붙여 메뉴별로 가른다.
     *
     * 열쇠를 하나로 합치면 서로 다른 메뉴를 주문하는 손님까지 줄을 서게 되어,
     * 비관적 락이 행 하나만 잠가 다른 메뉴 주문을 막지 않는 것과 성질이 달라진다.
     * 그러면 세 전략이 같은 조건에서 잰 것이 아니게 된다.
     */
    private static final String 열쇠_접두 = "lock:stock:menu:";

    /**
     * 열쇠를 기다리는 한계. 넘으면 StockLockTimeoutException 이다.
     *
     * 하네스의 대기 한계 30 초보다 작아야 한다. 크면 락을 못 얻은 스레드가 하네스에
     * 먼저 걸려 데드락으로 보고되는데, 실제로는 그냥 붐빈 것이다.
     *
     * 무한 대기로 두지 않는 이유는 열쇠가 안 풀리는 사고가 났을 때 요청 스레드가
     * 영영 붙잡히기 때문이다. 한계가 있으면 그때 503 이 나가고, 그 응답이 곧 단서다.
     */
    private static final long 대기_한계_초 = 10;

    private final RedissonClient redissonClient;
    private final StockRepository stockRepository;

    /**
     * Redis 에서 열쇠를 받아 온 뒤 재고 행을 읽는다.
     *
     * 읽기는 잠그지 않는 requireByMenuId 다. 낙관적 락과 같은 메서드를 쓴다.
     * 줄 세우는 일을 Redis 가 이미 했으므로 DB 행 락을 겹칠 이유가 없고,
     * 겹치면 무엇이 경쟁을 막고 있는지가 흐려져 이 전략을 재는 의미가 없어진다.
     *
     * 열쇠를 얻은 다음에 읽는 순서가 지켜져야 한다. 먼저 읽으면 그 값이 앞사람이
     * 커밋하기 전 것일 수 있고, 열쇠를 쥔 뒤에도 그 낡은 값을 그대로 깎게 된다.
     * PostgreSQL 기본 격리 수준이 READ COMMITTED 라 이 순서면 열쇠를 받은 시점에
     * 커밋된 최신 값을 읽는다.
     *
     * 여러 메뉴를 담은 주문은 이 메서드를 여러 번 부르고 열쇠도 여러 개 쥔다.
     * 부르는 순서를 호출자가 menuId 오름차순으로 고정하므로 열쇠를 쥐는 순서도 같다.
     * DB 행 락에서 데드락을 막던 그 정렬이 Redis 열쇠에서도 같은 일을 한다. NFR-CON-04.
     */
    @Override
    public Stock acquire(Long menuId) {
        // 열쇠를 잡기 전에 확인한다. 트랜잭션이 없으면 해제를 걸 자리가 없어
        // 잡는 순간 그 열쇠가 watchdog 만료까지 남는다.
        // 비관적 락은 같은 실수를 Hibernate 가 막아 주지만 이쪽은 아무도 막지 않는다.
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "분산 락은 트랜잭션 안에서만 얻을 수 있습니다: " + menuId
            );
        }

        RLock 열쇠 = redissonClient.getLock(열쇠_접두 + menuId);
        잠근다(열쇠, menuId);
        트랜잭션이_끝나면_푼다(열쇠);

        return stockRepository.requireByMenuId(menuId);
    }

    /**
     * 임대 시간을 -1 로 준다. Redisson 의 watchdog 에 맡긴다는 뜻이다.
     *
     * 숫자를 명시하면 트랜잭션이 그 시간을 넘길 때 열쇠가 먼저 풀린다. 그러면 두 손님이
     * 같은 재고 행에 동시에 닿는데, 그 사고는 예외로 드러나지 않고 재고가 가끔 어긋나는
     * 모양으로만 보인다. 락이 걸려 있으니 락을 의심하지도 않게 된다.
     *
     * watchdog 은 기본 30 초짜리 만료를 프로세스가 살아 있는 동안 계속 연장한다.
     * 프로세스가 죽으면 연장이 멈추고 30 초 뒤에 풀리므로, 열쇠가 영영 남는 경우도 없다.
     * 두 가지를 한꺼번에 얻는 대신 Redis 로 나가는 갱신 요청이 주기적으로 생긴다.
     */
    private void 잠근다(RLock 열쇠, Long menuId) {
        try {
            if (!열쇠.tryLock(대기_한계_초, -1, TimeUnit.SECONDS)) {
                throw new StockLockTimeoutException(menuId, 대기_한계_초);
            }
        } catch (InterruptedException e) {
            // 인터럽트를 삼키면 이 스레드를 멈추려던 쪽이 그 사실을 잃는다.
            // 호출자 입장에서는 열쇠를 못 얻고 돌아온다는 점이 같으므로 같은 예외로 낸다.
            Thread.currentThread().interrupt();
            throw new StockLockTimeoutException(menuId, 대기_한계_초);
        }
    }

    /**
     * 커밋이든 롤백이든 트랜잭션이 끝나면 열쇠를 놓는다.
     *
     * 등록이 실패하면 열쇠가 남는데, registerSynchronization 이 실패하는 유일한 경우가
     * 동기화가 활성이 아닐 때다. acquire 가 잠그기 전에 그것을 이미 확인했으므로
     * 여기서 다시 감싸지 않는다. 같은 스레드 안이라 그 사이에 상태가 바뀌지 않는다.
     *
     * 같은 menuId 로 두 번 들어오면 훅도 두 번 걸린다. RLock 이 재진입 가능해
     * 보유 횟수가 2 가 되므로 두 번 풀어야 실제로 놓인다. 수가 맞아떨어진다.
     */
    private void 트랜잭션이_끝나면_푼다(RLock 열쇠) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        열쇠.unlock();
                    }
                }
        );
    }
}
