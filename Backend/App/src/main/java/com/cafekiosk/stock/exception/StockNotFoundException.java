package com.cafekiosk.stock.exception;

/**
 * 있어야 할 재고 행이 없을 때 발생한다.
 *
 * 손님이나 점주가 요청을 고쳐 해결할 수 있는 종류가 아니라 서버 데이터가 어긋난 상태다.
 * 400 이나 409 로 흡수하지 않고 그대로 터뜨린다. GlobalExceptionHandler 에 매핑을 두지
 * 않는 것이 결정의 내용이므로 핸들러에 자리를 만들지 않는다. docs/ADR/ADR-0003 참고.
 *
 * ── IllegalStateException 을 쓰지 않는 이유 ─────────────────────────────────
 *
 * ADR-0003 은 IllegalStateException 을 던지기로 적었고 한동안 그렇게 동작했다.
 * 그때는 OrderService 의 private 헬퍼가 던졌기 때문이다. 판정을
 * StockRepository.requireByMenuId 로 옮기면서 던지는 자리가 Spring Data 리포지토리
 * 프록시 안으로 들어갔고, 그 순간 나가는 예외가 바뀐다.
 *
 * 프록시에는 PersistenceExceptionTranslationInterceptor 가 걸려 있고 default 메서드도
 * 그 체인을 지난다. 인터셉터가 부르는
 * EntityManagerFactoryUtils.convertJpaAccessExceptionIfPossible 은 IllegalStateException 과
 * IllegalArgumentException 을 가장 먼저 검사해 InvalidDataAccessApiUsageException 으로
 * 감싼다. JPA 명세가 프로바이더도 이 둘을 던질 수 있다고 규정했기 때문이다.
 * 그 목록에 없는 예외는 null 을 돌려받아 변환되지 않고 원본 그대로 나간다.
 *
 * 타입이 보존되는 것 말고도 얻는 것이 있다. 감싸이고 나면
 * InvalidDataAccessApiUsageException 하나가 재고 행 부재와 잘못된 인자를 함께 뜻하게 되어
 * 둘을 구분할 방법이 사라진다. 도메인 판단이 인프라 예외로 뭉개지는 셈이다.
 *
 * IllegalStateException 을 상속하지 않는 것도 같은 이유다. 상속하면 instanceof 검사에
 * 다시 걸려 원래 자리로 돌아간다.
 */
public class StockNotFoundException extends RuntimeException {

    public StockNotFoundException(Long menuId) {
        super("재고 행이 없는 메뉴입니다: " + menuId);
    }
}
