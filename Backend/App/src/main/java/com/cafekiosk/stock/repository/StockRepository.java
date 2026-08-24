package com.cafekiosk.stock.repository;

import com.cafekiosk.stock.entity.Stock;
import com.cafekiosk.stock.exception.StockNotFoundException;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByMenuId(Long menuId);

    /**
     * 메뉴 여러 개의 재고를 한 번에 읽는다. 메뉴 목록에 남은 수량을 붙이는 데 쓴다.
     *
     * 메뉴마다 findByMenuId 를 부르면 목록 길이만큼 쿼리가 나간다. 이 메서드로 읽으면
     * 메뉴가 몇 개든 쿼리는 한 방이고, 호출자가 menuId 로 맞춘다.
     *
     * 재고 행이 없는 메뉴는 결과에서 빠진다. 그 메뉴를 목록에서 지우는 것이 아니라
     * 수량을 모른다고 내려주는 것이 계약이므로, 맞추는 쪽이 빈자리를 그대로 다뤄야 한다.
     */
    List<Stock> findAllByMenuIdIn(Collection<Long> menuIds);

    /**
     * 반드시 있어야 하는 재고 행을 가져온다. 없으면 예외다.
     *
     * 재고 행 부재는 손님이나 점주가 요청을 고쳐 해결할 수 있는 종류가 아니라 서버 데이터가
     * 어긋난 상태다. 400 이나 409 로 흡수하지 않고 그대로 터뜨린다.
     * GlobalExceptionHandler 에 매핑이 없는 것은 의도다. 판단은 docs/ADR/ADR-0003 에 있다.
     *
     * 판단이 이 한 자리에 있는 이유는 주문 차감, 취소 복구, 점주의 재고 조정 셋이 같은 물음을
     * 갖기 때문이다. 각자 orElseThrow 를 쓰면 나중에 한쪽만 다른 예외로 바뀌는 길이 생긴다.
     *
     * 구현이 있는 default 메서드라 Spring Data 가 이름을 쿼리로 해석하지 않는다.
     * get 대신 require 를 쓰는 이유도 그것이다. get 은 실제 쿼리 접두어라 읽는 사람이
     * 파생 쿼리로 오해한다.
     *
     * 던지는 예외가 IllegalStateException 이 아닌 이유는 여기가 리포지토리 프록시 안이기
     * 때문이다. 프록시의 예외 변환 인터셉터가 IllegalStateException 을
     * InvalidDataAccessApiUsageException 으로 감싸 도메인 예외가 인프라 예외로 바뀐다.
     * 자세한 것은 StockNotFoundException 주석에 있다. 이 자리에서 던지는 예외를 바꿀 때는
     * 그 목록에 걸리지 않는 타입인지 먼저 확인한다.
     */
    default Stock requireByMenuId(Long menuId) {
        return findByMenuId(menuId)
                .orElseThrow(() -> new StockNotFoundException(menuId));
    }

    /**
     * 재고 행을 잠근 채로 읽는다. SELECT ... FOR UPDATE 가 나간다.
     *
     * 같은 행을 노리는 다른 트랜잭션은 이 트랜잭션이 끝날 때까지 여기서 멈춘다.
     * 락 없이 읽던 시절에는 열 손님이 전부 남은 수량 3 을 읽고 각자 2 를 계산해
     * 통째로 덮어썼다. 앞사람이 깎은 사실이 사라지는 lost update 다.
     *
     * ── 파생 쿼리 이름 대신 JPQL 을 직접 적는 이유 ─────────────────────────────
     *
     * findWithLockByMenuId 처럼 이름 사이에 끼워 넣어도 Spring Data 는 해석하지만,
     * 그러면 락이 걸린다는 사실을 이름 규칙을 아는 사람만 읽게 된다.
     *
     * 더 중요한 것은 조인이 없다는 사실이 눈에 보여야 한다는 점이다. PostgreSQL 은
     * outer join 의 nullable 쪽에 FOR UPDATE 를 걸면 거부한다. s.menu.id 는 연관의
     * 식별자 접근이라 stock.menu_id 컬럼을 그대로 읽고 조인을 만들지 않는데,
     * 이것이 우연이 아니라 이 쿼리가 성립하는 조건이다.
     * 여기에 join fetch 를 얹으려는 사람이 있다면 그 순간 이 쿼리가 죽는다.
     *
     * ── 잠그지 않는 조회를 남겨 두는 이유 ────────────────────────────────────
     *
     * 위의 findByMenuId 와 findAllByMenuIdIn 은 그대로 쓴다. 메뉴 목록이 남은 수량을
     * 붙이려고 재고를 읽을 때까지 행을 잠그면, 손님이 화면을 여는 것만으로 다른 손님의
     * 주문을 막게 된다. 읽기만 하는 경로와 깎을 작정으로 읽는 경로는 다른 메서드를 쓴다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Stock s where s.menu.id = :menuId")
    Optional<Stock> findByMenuIdForUpdate(@Param("menuId") Long menuId);

    /**
     * 차감하거나 복구할 재고 행을 잠근 채로 가져온다. 없으면 예외다.
     *
     * 행 부재 판정은 requireByMenuId 와 같은 규칙이고 같은 예외를 던진다.
     * 락이 붙었다고 해서 없는 행이 다른 사건이 되지는 않기 때문이다.
     * 던지는 예외가 StockNotFoundException 이어야 하는 이유는 그쪽 주석에 있다.
     *
     * 활성 트랜잭션 안에서만 성립한다. 비관적 락은 트랜잭션이 끝나야 풀리므로
     * 트랜잭션 없이 부르면 풀 자리가 없고 Hibernate 가 거부한다.
     */
    default Stock requireByMenuIdForUpdate(Long menuId) {
        return findByMenuIdForUpdate(menuId)
                .orElseThrow(() -> new StockNotFoundException(menuId));
    }
}
