package com.cafekiosk.order.service;

import com.cafekiosk.support.ConcurrencyResult;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Redisson 분산 락으로 같은 시나리오를 돌린다. 세 전략 비교표의 세 번째 행이다.
 *
 * 단언은 부모가 소유한다. 성공 3, 재고 부족 7, 최종 재고 0, 커밋된 주문 3 이 여기서도
 * 그대로 성립해야 한다는 것이 NFR-CON-03 이다. 이 행이 초록이 되는 순간 그 요구사항이
 * 요구한 세 전략이 전부 갖춰진다.
 *
 * ── Redis 를 Testcontainers 로 띄우는 이유 ─────────────────────────────────────
 *
 * docker-compose.yml 에 Redis 가 있고 로드맵도 그것을 띄우라고 적었지만, CI 는
 * docker compose 를 실행하지 않는다. PostgreSQL 이 CI 에서 도는 이유는
 * AbstractIntegrationTest 가 컨테이너를 직접 시작하기 때문이다. 로컬 6379 에 붙이면
 * 이 테스트만 CI 에서 죽고, 그 빨간불의 원인은 락이 아니라 Redis 부재가 된다.
 *
 * 컨테이너를 베이스가 아니라 이 클래스가 갖는 것은 Redis 를 쓰는 테스트가 이것 하나이기
 * 때문이다. 베이스에 올리면 비관적 락과 낙관적 락을 재는 동안에도 Redis 가 뜬다.
 *
 * @Testcontainers 와 @Container 를 붙이지 않는 것은 PostgreSQL 쪽과 같은 이유다.
 * 그 조합은 테스트 클래스가 바뀔 때마다 컨테이너를 재시작해 @DynamicPropertySource 가
 * 잡아둔 포트를 무효화한다. static 블록으로 직접 띄우면 JUnit 이 관여하지 않고
 * JVM 이 살아 있는 동안 유지된다. 정리는 Ryuk 이 한다.
 *
 * ── @SpringBootTest 를 다시 붙이지 않는다 ──────────────────────────────────────
 *
 * 붙이면 AbstractIntegrationTest 의 webEnvironment = RANDOM_PORT 를 덮어써 MOCK 으로
 * 되돌아간다. 자세한 것은 OptimisticOrderStockConcurrencyTest 주석에 있다.
 * 이 클래스가 컨텍스트를 하나 더 띄우는 것은 전략을 @ConditionalOnProperty 로 고르는
 * 이상 피할 수 없고, 기본값인 비관적 락 쪽이 아무것도 붙이지 않아 그 수가 둘로 끝난다.
 */
@TestPropertySource(properties = "cafekiosk.stock.lock-strategy=distributed")
public class DistributedOrderStockConcurrencyTest extends AbstractOrderStockConcurrencyTest {

    static final GenericContainer<?> redis;

    static {
        // docker-compose.yml 과 같은 7 로 맞춘다. 버전이 갈리면 로컬에서 통과한 것이
        // CI 에서 다르게 도는데, 락 동작에서 그 차이는 눈에 잘 띄지 않는다.
        redis = new GenericContainer<>("redis:7").withExposedPorts(6379);
        redis.start();
    }

    /**
     * 컨테이너가 배정받은 포트를 분산 락 설정에 꽂는다.
     *
     * 부모가 DataSource 를 주입하는 것과 함께 수집된다. 상속 계층의
     * @DynamicPropertySource 는 전부 실행되므로 여기서 다시 적을 필요가 없다.
     */
    @DynamicPropertySource
    static void overrideRedisAddress(DynamicPropertyRegistry registry) {
        registry.add("cafekiosk.stock.redis-address",
                () -> "redis://%s:%d".formatted(redis.getHost(), redis.getMappedPort(6379)));
    }

    /**
     * 분산 락에서는 재시도가 한 번도 일어나지 않는다.
     *
     * 열쇠를 쥔 스레드만 재고 행에 닿으므로 두 스레드가 같은 수량을 함께 읽는 일이 없다.
     * Stock.version 은 그대로 살아 있지만 어긋날 창이 없어 조용히 지나간다.
     * 비관적 락이 0 인 것과 같은 성질이고, 잠그는 자리가 DB 행이냐 Redis 열쇠냐만 다르다.
     *
     * 그래서 비교표에서 이 행과 비관적 락 행의 차이는 재시도가 아니라 소요시간에 나온다.
     * 네트워크를 한 번 더 왕복하는 대가가 거기 찍힌다.
     *
     * 이 0 이 잡는 배선 사고도 있다. 열쇠를 잡지 못한 채 조회만 하게 되면 낙관적 락과
     * 같은 모양이 되어 재시도가 0 보다 커진다. 그때 이 줄이 먼저 빨개진다.
     */
    @Override
    protected void 재시도를_검사한다(ConcurrencyResult 결과) {
        assertThat(결과.재시도())
                .as("열쇠를 쥔 스레드만 재고에 닿으므로 버전이 어긋날 창이 없다. %s", 결과.요약())
                .isZero();
    }
}
