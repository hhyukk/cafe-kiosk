package com.cafekiosk.order.service;

import com.cafekiosk.support.ConcurrencyResult;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 낙관적 락으로 같은 시나리오를 돌린다. 세 전략 비교표의 두 번째 행이다.
 *
 * 단언은 부모가 소유한다. 성공 3, 재고 부족 7, 최종 재고 0, 커밋된 주문 3 이 여기서도
 * 그대로 성립해야 한다는 것이 NFR-CON-03 의 내용이다. 잠그는 방식이 달라도 손님이
 * 받는 결과는 같아야 하고, 달라지는 것은 그 결과에 이르는 비용뿐이다.
 *
 * ── @SpringBootTest 를 다시 붙이지 않는다 ──────────────────────────────────────
 *
 * 프로퍼티를 주려고 @SpringBootTest(properties = ...) 를 쓰고 싶어지는데, 그러면
 * AbstractIntegrationTest 의 @SpringBootTest(webEnvironment = RANDOM_PORT) 를
 * 덮어써 webEnvironment 가 기본값 MOCK 으로 되돌아간다. 실제 Tomcat 이 뜨지 않는
 * 컨텍스트가 하나 더 생기는 것인데, 이 테스트는 서비스를 직접 부르므로 당장은 통과한다.
 * 조용히 다른 환경에서 도는 테스트가 되는 것이 문제다.
 *
 * @TestPropertySource 는 @SpringBootTest 를 재선언하지 않으므로 나머지 설정이 전부
 * 상속되고 캐시 키만 달라진다. 그 결과 이 클래스만 컨텍스트를 새로 띄운다.
 *
 * 컨텍스트가 하나 더 뜨는 비용은 전략을 @ConditionalOnProperty 로 고르는 이상 피할 수
 * 없다. 대신 비관적 락 쪽이 아무 어노테이션도 갖지 않아 기본 컨텍스트를 공유하므로,
 * 전략이 셋으로 늘어도 추가되는 컨텍스트는 기본값이 아닌 둘뿐이다.
 */
@TestPropertySource(properties = "cafekiosk.stock.lock-strategy=optimistic")
public class OptimisticOrderStockConcurrencyTest extends AbstractOrderStockConcurrencyTest {

    /**
     * 낙관적 락에서는 재시도가 반드시 일어난다. 부모는 이 기대를 전원이 성공해야 하는 조건에서 묻는다.
     *
     * 재고 100 인 한 행에 열 스레드가 잠그지 않고 달려들면, 열이 모두 커밋해야 하므로 누가 먼저
     * 커밋할 때마다 같은 버전을 읽어 둔 나머지가 충돌을 받는다. 재 보니 판마다 15 번에서 29 번이었다.
     *
     * 처음에는 마지막 한 잔 조건에서 물었다. 열 스레드가 같은 수량을 읽으니 아홉이 충돌을 받고
     * 그중 둘이 다시 시도해 성공한다고 봤다. 실제로는 성공한 세 스레드가 충돌 없이 끝나는 판이
     * 나왔다. 세는 것이 성공한 스레드의 재시도뿐이라 그 판에서는 0 이 되고, 이 단언이 락이 아니라
     * 도착 순서를 탓하게 된다.
     *
     * 정확한 숫자를 못 박지 않는 이유는 그 값이 스케줄링에 따라 달라지기 때문이다.
     * 못 박으면 CI 부하에 따라 흔들리는 테스트가 된다. 흔들리는 테스트는 진짜 실패를 가린다.
     *
     * 0 보다 크다는 것만 본다. 이 느슨한 단언이 잡는 것은 배선 사고다. 파사드를 거치지
     * 않게 되거나 Stock.version 이 사라지면 재시도가 0 이 되는데, 스레드가 우연히 순차로 돌면
     * 그 상태로도 나머지 단언이 통과할 수 있다. 이 줄이 그 우연을 막는다.
     */
    @Override
    protected void 재시도를_검사한다(ConcurrencyResult 결과) {
        assertThat(결과.재시도())
                .as("잠그지 않고 읽었으니 충돌이 있어야 한다. 0 이면 파사드나 @Version 배선을 본다. %s",
                        결과.요약())
                .isPositive();
    }
}
