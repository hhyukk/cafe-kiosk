package com.cafekiosk.stock.repository;

import com.cafekiosk.stock.entity.Stock;
import com.cafekiosk.stock.exception.StockNotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByMenuId(Long menuId);

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
}
